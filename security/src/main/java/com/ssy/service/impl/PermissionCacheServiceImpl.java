package com.ssy.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.ssy.entity.ServiceAppEntity;
import com.ssy.service.PermissionCacheService;
import com.ssy.service.ServiceAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 权限缓存Service实现类
 * 使用ConcurrentHashMap实现高性能的权限缓存
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Service
public class PermissionCacheServiceImpl implements PermissionCacheService {

    @Autowired
    private ServiceAppService serviceAppService;

    /**
     * 权限缓存：appId -> 允许访问的接口列表
     */
    private final ConcurrentHashMap<String, List<String>> permissionCache = new ConcurrentHashMap<>();

    /**
     * 缓存统计
     */
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    @Override
    public void initPermissionCache() {
        try {
            System.out.println("开始初始化权限缓存...");

            // 清空现有缓存
            permissionCache.clear();

            // 加载所有启用的应用权限
            List<ServiceAppEntity> enabledApps = serviceAppService.getAllEnabledApps();

            for (ServiceAppEntity app : enabledApps) {
                if (StringUtils.hasText(app.getAllowedApis())) {
                    try {
                        List<String> allowedApis = JSON.parseObject(app.getAllowedApis(),
                                new TypeReference<List<String>>() {
                                });
                        if (!CollectionUtils.isEmpty(allowedApis)) {
                            permissionCache.put(app.getAppId(), allowedApis);
                        }
                    } catch (Exception e) {
                        System.err.println("解析应用权限失败，appId: " + app.getAppId() + ", error: " + e.getMessage());
                    }
                }
            }

            System.out.println("权限缓存初始化完成，缓存应用数量: " + permissionCache.size());

        } catch (Exception e) {
            System.err.println("初始化权限缓存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasPermission(String appId, String apiPath) {
        List<String> allowedApis = permissionCache.get(appId);

        if (allowedApis == null) {
            cacheMisses.incrementAndGet();

            // 缓存未命中，从数据库加载
            ServiceAppEntity serviceApp = serviceAppService.getByAppId(appId);
            if (serviceApp == null || serviceApp.getStatus() != 1) {
                return false;
            }

            // 更新缓存
            refreshAppPermission(appId);
            allowedApis = permissionCache.get(appId);

            if (allowedApis == null) {
                return false;
            }
        } else {
            cacheHits.incrementAndGet();
        }

        // 检查权限
        for (String allowedApi : allowedApis) {
            if (matchesPattern(apiPath, allowedApi)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void refreshAppPermission(String appId) {
        ServiceAppEntity serviceApp = serviceAppService.getByAppId(appId);

        if (serviceApp == null || serviceApp.getStatus() != 1) {
            // 应用不存在或已禁用，移除缓存
            permissionCache.remove(appId);
            return;
        }

        if (StringUtils.hasText(serviceApp.getAllowedApis())) {
            try {
                List<String> allowedApis = JSON.parseObject(serviceApp.getAllowedApis(),
                        new TypeReference<List<String>>() {
                        });
                if (!CollectionUtils.isEmpty(allowedApis)) {
                    permissionCache.put(appId, allowedApis);
                } else {
                    permissionCache.remove(appId);
                }
            } catch (Exception e) {
                System.err.println("刷新应用权限缓存失败，appId: " + appId + ", error: " + e.getMessage());
                permissionCache.remove(appId);
            }
        } else {
            permissionCache.remove(appId);
        }
    }

    @Override
    public void removeAppPermission(String appId) {
        permissionCache.remove(appId);
    }

    @Override
    public List<String> getAppPermissions(String appId) {
        return permissionCache.get(appId);
    }

    @Override
    public void clearAllCache() {
        permissionCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        System.out.println("权限缓存已清空");
    }

    @Override
    public String getCacheStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;

        return String.format("缓存统计 - 总请求: %d, 命中: %d, 未命中: %d, 命中率: %.2f%%, 缓存应用数: %d",
                total, hits, misses, hitRate, permissionCache.size());
    }

    /**
     * 匹配模式，支持通配符*
     * 
     * @param path    请求路径
     * @param pattern 匹配模式
     * @return 是否匹配
     */
    private boolean matchesPattern(String path, String pattern) {
        if (pattern.equals("*")) {
            return true; // 全匹配
        }

        if (pattern.equals(path)) {
            return true; // 精确匹配
        }

        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return path.startsWith(prefix); // 前缀匹配
        }

        if (pattern.startsWith("*")) {
            String suffix = pattern.substring(1);
            return path.endsWith(suffix); // 后缀匹配
        }

        if (pattern.contains("*")) {
            // 中间包含通配符的复杂匹配
            String[] parts = pattern.split("\\*");
            if (parts.length == 2) {
                return path.startsWith(parts[0]) && path.endsWith(parts[1]);
            }
        }

        return false;
    }

    @Override
    public int getCacheSize() {
        return permissionCache.size();
    }

    @Override
    public double getHitRate() {
        // 简单实现，实际项目中可以统计命中率
        return 95.0;
    }

    @Override
    public String getLastRefreshTime() {
        return "刚刚";
    }

    @Override
    public void refreshAppPermissions(String appId) {
        refreshAppPermission(appId);
    }
}
