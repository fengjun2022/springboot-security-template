package com.ssy.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ssy.entity.ServiceAppEntity;
import com.ssy.entity.ServiceTokenEntity;
import com.ssy.mapper.ServiceTokenMapper;
import com.ssy.service.ServiceAppService;
import com.ssy.service.ServiceTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务TokenService实现类
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Service
public class ServiceTokenServiceImpl implements ServiceTokenService {

    @Autowired
    private ServiceTokenMapper serviceTokenMapper;

    @Autowired
    private ServiceAppService serviceAppService;

    // 用于签发永久token的密钥
    private static final String TOKEN_SECRET = "service_token_secret_key_2025_zxy_hospital_admin";

    /**
     * Token缓存：token -> ServiceTokenEntity
     * 只缓存验证通过的token，避免每次都查数据库
     */
    private final ConcurrentHashMap<String, CachedTokenInfo> tokenCache = new ConcurrentHashMap<>();

    /**
     * 缓存的Token信息，包含最后更新时间用于控制数据库更新频率
     */
    private static class CachedTokenInfo {
        private final ServiceTokenEntity tokenEntity;
        private LocalDateTime lastDatabaseUpdate; // 上次数据库更新时间

        public CachedTokenInfo(ServiceTokenEntity tokenEntity) {
            this.tokenEntity = tokenEntity;
            this.lastDatabaseUpdate = LocalDateTime.now();
        }

        public ServiceTokenEntity getTokenEntity() {
            return tokenEntity;
        }

        public LocalDateTime getLastDatabaseUpdate() {
            return lastDatabaseUpdate;
        }

        public void updateLastDatabaseUpdate() {
            this.lastDatabaseUpdate = LocalDateTime.now();
        }
    }

    @Override
    public ServiceTokenEntity issueToken(String appId, String authCode, String issueBy) {
        // 验证应用
        ServiceAppEntity serviceApp = serviceAppService.validateApp(appId, authCode);
        if (serviceApp == null) {
            throw new RuntimeException("应用验证失败，appId或authCode错误");
        }

        // 先失效该应用的所有已有token
        invalidateTokensByAppId(appId);

        // 生成永久token
        String token = generatePermanentToken(appId, serviceApp.getAppName());

        // 创建token记录
        ServiceTokenEntity serviceToken = new ServiceTokenEntity();
        serviceToken.setAppId(appId);
        serviceToken.setToken(token);
        serviceToken.setTokenType("permanent");
        serviceToken.setStatus(1);
        serviceToken.setIssueTime(LocalDateTime.now());
        serviceToken.setIssueBy(issueBy);
        serviceToken.setRemark("永久token，用于服务间调用");

        // 插入数据库
        serviceTokenMapper.insert(serviceToken);

        // 清空相关缓存
        clearTokenCacheByAppId(appId);

        return serviceToken;
    }

    @Override
    public ServiceTokenEntity validateToken(String token) {
        // 先检查缓存
        CachedTokenInfo cachedInfo = tokenCache.get(token);

        if (cachedInfo != null) {
            System.err.println("=== Token缓存命中: " + token.substring(0, 20) + "...");

            // 验证JWT token（这个验证很快，不需要缓存）
            try {
                Algorithm algorithm = Algorithm.HMAC256(TOKEN_SECRET);
                JWT.require(algorithm).build().verify(token);

                // 控制数据库更新频率，只有距离上次更新超过1分钟才更新数据库
                LocalDateTime now = LocalDateTime.now();
                if (ChronoUnit.MINUTES.between(cachedInfo.getLastDatabaseUpdate(), now) >= 5) {
                    updateLastUsedTime(token);
                    cachedInfo.updateLastDatabaseUpdate();
                }

                return cachedInfo.getTokenEntity();
            } catch (Exception e) {
                // JWT验证失败，从缓存中移除
                System.err.println("=== JWT验证失败，移除缓存");
                tokenCache.remove(token);
                return null;
            }
        }


        // 缓存未命中，查询数据库
        ServiceTokenEntity serviceToken = serviceTokenMapper.selectByToken(token);
        if (serviceToken != null) {
            // 验证token是否有效
            try {
                // 验证JWT token
                Algorithm algorithm = Algorithm.HMAC256(TOKEN_SECRET);
                JWT.require(algorithm).build().verify(token);

                // 更新最后使用时间
                updateLastUsedTime(token);

                // 放入缓存
                tokenCache.put(token, new CachedTokenInfo(serviceToken));

                return serviceToken;
            } catch (Exception e) {
                // token验证失败
                System.err.println("=== JWT Token验证失败: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    @Override
    public ServiceTokenEntity getTokenByAppId(String appId) {
        return serviceTokenMapper.selectByAppId(appId);
    }

    @Override
    public void updateLastUsedTime(String token) {
        serviceTokenMapper.updateLastUsedTime(token, LocalDateTime.now());
    }

    @Override
    public void invalidateToken(Long tokenId) {
        serviceTokenMapper.updateStatus(tokenId, 0);
        // 清空整个缓存，因为不知道哪个token对应这个ID
        clearAllTokenCache();
    }

    @Override
    public void invalidateTokensByAppId(String appId) {
        serviceTokenMapper.invalidateByAppId(appId);
        // 清空相关缓存
        clearTokenCacheByAppId(appId);
    }

    @Override
    public ServiceTokenEntity regenerateToken(String appId, String authCode, String issueBy) {
        return issueToken(appId, authCode, issueBy);
    }

    /**
     * 生成永久token
     *
     * @param appId   应用ID
     * @param appName 应用名称
     * @return JWT token
     */
    private String generatePermanentToken(String appId, String appName) {
        Algorithm algorithm = Algorithm.HMAC256(TOKEN_SECRET);

        return JWT.create()
                .withIssuer("zxy-hospital-admin") // 签发者
                .withSubject(appId) // 主题(应用ID)
                .withClaim("appId", appId) // 应用ID
                .withClaim("appName", appName) // 应用名称
                .withClaim("type", "permanent") // token类型
                .withIssuedAt(new Date()) // 签发时间
                // 永久token不设置过期时间
                .sign(algorithm);
    }

    @Override
    public List<ServiceTokenEntity> getAllTokens() {
        return serviceTokenMapper.selectAll();
    }

    /**
     * 清空指定应用的Token缓存
     */
    private void clearTokenCacheByAppId(String appId) {
        tokenCache.entrySet().removeIf(entry ->
                appId.equals(entry.getValue().getTokenEntity().getAppId()));
        System.err.println("=== 已清空appId: " + appId + " 的Token缓存");
    }

    /**
     * 清空所有Token缓存
     */
    private void clearAllTokenCache() {
        tokenCache.clear();
        System.err.println("=== 已清空所有Token缓存");
    }

    /**
     * 获取缓存统计信息
     */
    public String getTokenCacheStats() {
        return String.format("Token缓存统计 - 缓存大小: %d", tokenCache.size());
    }
}