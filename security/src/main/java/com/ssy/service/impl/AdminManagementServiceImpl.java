package com.ssy.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.pojo.entity.UserEntity;
import com.ssy.controller.AdminManagementController.AppCreateDTO;
import com.ssy.controller.AdminManagementController.UserCreateDTO;
import com.ssy.entity.ApiEndpointEntity;
import com.ssy.entity.ServiceAppEntity;
import com.ssy.entity.ServiceTokenEntity;
import com.ssy.mapper.ApiEndpointMapper;
import com.ssy.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理员权限管理服务实现类
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Service
public class AdminManagementServiceImpl implements AdminManagementService {

    private static final Logger logger = LoggerFactory.getLogger(AdminManagementServiceImpl.class);

    @Autowired
    private UserService userService;

    @Autowired
    private ServiceAppService serviceAppService;

    @Autowired
    private ServiceTokenService serviceTokenService;

    @Autowired
    private ApiEndpointService apiEndpointService;

    @Autowired
    private PermissionCacheService permissionCacheService;

    @Autowired
    private ApiEndpointMapper apiEndpointMapper;

    // 系统日志缓存（实际项目中应该使用数据库或日志收集系统）
    private final List<Map<String, Object>> systemLogs = Collections.synchronizedList(new ArrayList<>());

    @Override
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 用户统计
            stats.put("totalUsers", userService.getUserCount());
            stats.put("activeUsers", userService.getActiveUserCount());

            // 应用统计
            List<ServiceAppEntity> apps = serviceAppService.getAllApps();
            stats.put("totalApps", apps.size());
            stats.put("activeApps", apps.stream().mapToInt(app -> app.getStatus() == 1 ? 1 : 0).sum());

            // 接口统计
            ApiEndpointService.PageResult<ApiEndpointEntity> endpoints = apiEndpointService.getEndpointsByPage(1,
                    Integer.MAX_VALUE, null, null);
            stats.put("totalEndpoints", endpoints.getTotal());
            stats.put("activeEndpoints", endpoints.getRecords().stream()
                    .mapToInt(ep -> ep.getStatus() == 1 ? 1 : 0).sum());

            // Token统计
            List<ServiceTokenEntity> tokens = serviceTokenService.getAllTokens();
            stats.put("totalTokens", tokens.size());
            stats.put("validTokens", tokens.stream()
                    .filter(token -> token != null && token.getIsValid() != null)
                    .mapToInt(token -> token.getIsValid() == 1 ? 1 : 0)
                    .sum());

            // 模块统计
            List<String> modules = apiEndpointService.getAllModuleGroups();
            stats.put("totalModules", modules.size());

        } catch (Exception e) {
            logger.error("获取系统统计信息失败", e);
            stats.put("error", "获取统计信息失败: " + e.getMessage());
        }

        return stats;
    }

    @Override
    public List<String> getAllRoles() {
        return Arrays.asList("ADMIN", "MANAGER", "USER", "GUEST");
    }

    @Override
    @Transactional
    public void createUser(UserCreateDTO userDTO) {
        try {
            UserEntity user = new UserEntity();
            user.setUsername(userDTO.getUsername());
            user.setPassword(userDTO.getPassword()); // 实际项目中需要加密
            // user.setEmail(userDTO.getEmail()); // 暂时注释
            // user.setPhone(userDTO.getPhone()); // 暂时注释
            // user.setStatus(1); // 暂时注释 - 默认启用
            user.setAuthorities(userDTO.getRoles());

            userService.createUser(user);

            // 记录日志
            addSystemLog("用户管理", "创建用户: " + userDTO.getUsername(), "SUCCESS");

        } catch (Exception e) {
            addSystemLog("用户管理", "创建用户失败: " + userDTO.getUsername(), "ERROR");
            throw new RuntimeException("创建用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void assignUserRoles(Long userId, List<String> roles) {
        try {
            userService.updateUserRoles(userId, roles);
            addSystemLog("权限管理", "分配用户角色: userId=" + userId, "SUCCESS");
        } catch (Exception e) {
            addSystemLog("权限管理", "分配用户角色失败: userId=" + userId, "ERROR");
            throw new RuntimeException("分配角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ServiceAppEntity createApp(AppCreateDTO appDTO) {
        try {
            ServiceAppEntity app = serviceAppService.registerApp(
                    appDTO.getAppName(),
                    appDTO.getAllowedApis(),
                    "admin",
                    appDTO.getRemark());

            addSystemLog("应用管理", "创建应用: " + appDTO.getAppName(), "SUCCESS");
            return app;

        } catch (Exception e) {
            addSystemLog("应用管理", "创建应用失败: " + appDTO.getAppName(), "ERROR");
            throw new RuntimeException("创建应用失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void assignAppPermissions(String appId, List<String> apiPaths) {
        try {
            ServiceAppEntity app = serviceAppService.getByAppId(appId);
            if (app == null) {
                throw new RuntimeException("应用不存在");
            }

            app.setAllowedApiList(apiPaths);
            serviceAppService.updateApp(app);

            // 刷新权限缓存
            permissionCacheService.refreshAppPermissions(appId);

            addSystemLog("权限管理", "分配应用权限: appId=" + appId, "SUCCESS");

        } catch (Exception e) {
            addSystemLog("权限管理", "分配应用权限失败: appId=" + appId, "ERROR");
            throw new RuntimeException("分配权限失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ServiceTokenEntity refreshAppToken(String appId) {
        try {
            ServiceAppEntity app = serviceAppService.getByAppId(appId);
            if (app == null) {
                throw new RuntimeException("应用不存在");
            }

            // 先失效旧Token
            serviceTokenService.invalidateTokensByAppId(appId);

            // 重新签发Token
            ServiceTokenEntity newToken = serviceTokenService.issueToken(appId, app.getAuthCode(), "admin");

            addSystemLog("Token管理", "刷新Token: appId=" + appId, "SUCCESS");
            return newToken;

        } catch (Exception e) {
            addSystemLog("Token管理", "刷新Token失败: appId=" + appId, "ERROR");
            throw new RuntimeException("Token刷新失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void updateEndpointStatus(Long endpointId, Integer status) {
        try {
            ApiEndpointEntity endpoint = apiEndpointService.getEndpointById(endpointId);
            if (endpoint == null) {
                throw new RuntimeException("接口不存在");
            }

            endpoint.setStatus(status);
            apiEndpointService.updateEndpoint(endpoint);

            addSystemLog("接口管理", "更新接口状态: endpointId=" + endpointId + ", status=" + status, "SUCCESS");

        } catch (Exception e) {
            addSystemLog("接口管理", "更新接口状态失败: endpointId=" + endpointId, "ERROR");
            throw new RuntimeException("更新接口状态失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getSystemLogs() {
        // 返回最近的100条日志
        List<Map<String, Object>> recentLogs = new ArrayList<>(systemLogs);
        Collections.reverse(recentLogs);
        return recentLogs.size() > 100 ? recentLogs.subList(0, 100) : recentLogs;
    }

    @Override
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> systemInfo = new HashMap<>();

        try {
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            // JVM信息
            systemInfo.put("jvmName", runtimeBean.getVmName());
            systemInfo.put("jvmVersion", runtimeBean.getVmVersion());
            systemInfo.put("startTime", new Date(runtimeBean.getStartTime()));
            systemInfo.put("uptime", runtimeBean.getUptime());

            // 内存信息
            long totalMemory = memoryBean.getHeapMemoryUsage().getMax();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            systemInfo.put("totalMemory", totalMemory);
            systemInfo.put("usedMemory", usedMemory);
            systemInfo.put("freeMemory", totalMemory - usedMemory);
            systemInfo.put("memoryUsagePercent", (double) usedMemory / totalMemory * 100);

            // 操作系统信息
            systemInfo.put("osName", System.getProperty("os.name"));
            systemInfo.put("osVersion", System.getProperty("os.version"));
            systemInfo.put("osArch", System.getProperty("os.arch"));

            // Java信息
            systemInfo.put("javaVersion", System.getProperty("java.version"));
            systemInfo.put("javaHome", System.getProperty("java.home"));

        } catch (Exception e) {
            logger.error("获取系统信息失败", e);
            systemInfo.put("error", "获取系统信息失败: " + e.getMessage());
        }

        return systemInfo;
    }

    @Override
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> cacheStatus = new HashMap<>();

        try {
            // 权限缓存状态
            Map<String, Object> permissionCache = new HashMap<>();
            permissionCache.put("size", permissionCacheService.getCacheSize());
            permissionCache.put("hitRate", permissionCacheService.getHitRate());
            permissionCache.put("lastRefreshTime", permissionCacheService.getLastRefreshTime());

            cacheStatus.put("permissionCache", permissionCache);

            // 其他缓存状态可以在这里添加

        } catch (Exception e) {
            logger.error("获取缓存状态失败", e);
            cacheStatus.put("error", "获取缓存状态失败: " + e.getMessage());
        }

        return cacheStatus;
    }

    @Override
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // 数据库连接状态
            status.put("databaseStatus", "CONNECTED");

            // 缓存状态
            status.put("cacheStatus", "ACTIVE");

            // 内存使用率
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long totalMemory = memoryBean.getHeapMemoryUsage().getMax();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            status.put("memoryUsage", (double) usedMemory / totalMemory * 100);

            // 系统运行时间
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            status.put("uptime", runtimeBean.getUptime());

            // 当前时间
            status.put("currentTime", new Date());

            // 活跃连接数等其他指标
            status.put("activeConnections", getActiveConnectionCount());

        } catch (Exception e) {
            logger.error("获取系统状态失败", e);
            status.put("error", "获取系统状态失败: " + e.getMessage());
        }

        return status;
    }

    @Override
    public void clearSystemCache() {
        try {
            permissionCacheService.clearAllCache();
            addSystemLog("系统管理", "清理系统缓存", "SUCCESS");
        } catch (Exception e) {
            addSystemLog("系统管理", "清理系统缓存失败", "ERROR");
            throw new RuntimeException("清理缓存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 添加系统日志
     */
    private void addSystemLog(String module, String operation, String result) {
        Map<String, Object> log = new HashMap<>();
        log.put("timestamp", LocalDateTime.now());
        log.put("module", module);
        log.put("operation", operation);
        log.put("result", result);
        log.put("operator", "admin"); // 实际项目中应该从Security Context获取

        systemLogs.add(log);

        // 限制日志数量，避免内存溢出
        if (systemLogs.size() > 1000) {
            systemLogs.remove(0);
        }

        logger.info("系统日志: [{}] {} - {}", module, operation, result);
    }

    /**
     * 获取活跃连接数（示例）
     */
    private int getActiveConnectionCount() {
        // 这里应该从连接池获取实际的连接数
        return 10; // 示例值
    }

    // ========== 管理员登录相关方法 ==========

    /**
     * 生成管理员JWT Token
     */
    @Override
    public String generateAdminToken(String username) {
        try {
            // 使用ServiceTokenService中的JWT生成逻辑
            // 这里创建一个临时的ServiceAppEntity来复用现有的token生成逻辑
            ServiceAppEntity tempApp = new ServiceAppEntity();
            tempApp.setAppId("admin-system");
            tempApp.setAppName("Admin System");

            // 直接使用JWT生成token
            String token = generateAdminJWT(username);
            return token;
        } catch (Exception e) {
            logger.error("生成管理员token失败", e);
            throw new RuntimeException("生成管理员token失败: " + e.getMessage());
        }
    }

    /**
     * 验证管理员Token
     */
    @Override
    public boolean validateAdminToken(String token) {
        try {
            // 验证JWT token
            Algorithm algorithm = Algorithm.HMAC256(getAdminSecretKey());
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);

            // 检查是否为管理员token
            String subject = jwt.getSubject();
            return "admin".equals(subject) || "system".equals(subject);
        } catch (Exception e) {
            logger.warn("管理员token验证失败", e);
            return false;
        }
    }

    /**
     * 从Token中获取用户信息
     */
    @Override
    public Map<String, Object> getUserFromToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(getAdminSecretKey());
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("username", jwt.getSubject());
            userInfo.put("role", jwt.getClaim("role").asString());
            userInfo.put("issuedAt", jwt.getIssuedAt());
            userInfo.put("expiresAt", jwt.getExpiresAt());

            return userInfo;
        } catch (Exception e) {
            logger.warn("从token获取用户信息失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 生成管理员JWT Token
     */
    private String generateAdminJWT(String username) {
        Algorithm algorithm = Algorithm.HMAC256(getAdminSecretKey());

        return JWT.create()
                .withSubject(username)
                .withClaim("role", "ADMIN")
                .withClaim("type", "admin")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 8 * 60 * 60 * 1000)) // 8小时过期
                .sign(algorithm);
    }

    /**
     * 获取管理员密钥
     */
    private String getAdminSecretKey() {
        // 使用固定的管理员密钥，实际项目中应该从配置文件中读取
        return "admin_jwt_secret_key_2025_zxy_hospital_system";
    }
}
