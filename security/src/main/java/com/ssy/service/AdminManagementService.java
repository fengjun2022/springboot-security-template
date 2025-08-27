package com.ssy.service;

import com.ssy.controller.AdminManagementController.AppCreateDTO;
import com.ssy.controller.AdminManagementController.UserCreateDTO;
import com.ssy.entity.ServiceAppEntity;
import com.ssy.entity.ServiceTokenEntity;

import java.util.List;
import java.util.Map;

/**
 * 管理员权限管理服务接口
 * 提供完整的管理功能
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
public interface AdminManagementService {

    /**
     * 获取系统统计信息
     */
    Map<String, Object> getSystemStats();

    /**
     * 获取所有角色
     */
    List<String> getAllRoles();

    /**
     * 创建用户
     */
    void createUser(UserCreateDTO userDTO);

    /**
     * 分配用户角色
     */
    void assignUserRoles(Long userId, List<String> roles);

    /**
     * 创建应用
     */
    ServiceAppEntity createApp(AppCreateDTO appDTO);

    /**
     * 分配应用权限
     */
    void assignAppPermissions(String appId, List<String> apiPaths);

    /**
     * 刷新应用Token
     */
    ServiceTokenEntity refreshAppToken(String appId);

    /**
     * 更新接口状态
     */
    void updateEndpointStatus(Long endpointId, Integer status);

    /**
     * 获取系统日志
     */
    List<Map<String, Object>> getSystemLogs();

    /**
     * 获取系统信息
     */
    Map<String, Object> getSystemInfo();

    /**
     * 获取缓存状态
     */
    Map<String, Object> getCacheStatus();

    /**
     * 获取系统实时状态
     */
    Map<String, Object> getSystemStatus();

    /**
     * 清理系统缓存
     */
    void clearSystemCache();

    // ========== 管理员登录相关方法 ==========

    /**
     * 生成管理员JWT Token
     */
    String generateAdminToken(String username);

    /**
     * 验证管理员Token
     */
    boolean validateAdminToken(String token);

    /**
     * 从Token中获取用户信息
     */
    Map<String, Object> getUserFromToken(String token);
}
