package com.ssy.service;

import java.util.List;

/**
 * 权限缓存Service接口
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
public interface PermissionCacheService {

    /**
     * 初始化权限缓存
     * 在应用启动时调用，加载所有启用应用的权限数据到缓存
     */
    void initPermissionCache();

    /**
     * 检查应用是否有权限访问指定接口
     * 
     * @param appId   应用ID
     * @param apiPath 接口路径
     * @return true-有权限，false-无权限
     */
    boolean hasPermission(String appId, String apiPath);

    /**
     * 刷新指定应用的权限缓存
     * 
     * @param appId 应用ID
     */
    void refreshAppPermission(String appId);

    /**
     * 清除指定应用的权限缓存
     * 
     * @param appId 应用ID
     */
    void removeAppPermission(String appId);

    /**
     * 获取应用的权限列表
     * 
     * @param appId 应用ID
     * @return 允许访问的接口列表
     */
    List<String> getAppPermissions(String appId);

    /**
     * 清除所有权限缓存
     */
    void clearAllCache();

    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息
     */
    String getCacheStats();

    /**
     * 获取缓存大小
     */
    int getCacheSize();

    /**
     * 获取缓存命中率
     */
    double getHitRate();

    /**
     * 获取最后刷新时间
     */
    String getLastRefreshTime();

    /**
     * 刷新指定应用权限
     */
    void refreshAppPermissions(String appId);
}
