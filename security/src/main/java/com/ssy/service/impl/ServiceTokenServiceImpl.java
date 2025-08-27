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
import java.util.Date;
import java.util.List;

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

        return serviceToken;
    }

    @Override
    public ServiceTokenEntity validateToken(String token) {
        ServiceTokenEntity serviceToken = serviceTokenMapper.selectByToken(token);
        if (serviceToken != null) {
            // 验证token是否有效
            try {
                // 验证JWT token
                Algorithm algorithm = Algorithm.HMAC256(TOKEN_SECRET);
                JWT.require(algorithm).build().verify(token);

                // 更新最后使用时间
                updateLastUsedTime(token);

                return serviceToken;
            } catch (Exception e) {
                // token验证失败
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
    }

    @Override
    public void invalidateTokensByAppId(String appId) {
        serviceTokenMapper.invalidateByAppId(appId);
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
}
