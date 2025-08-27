package com.ssy.service;

import com.ssy.entity.ServiceAppEntity;

import java.util.List;

/**
 * 服务应用Service接口
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
public interface ServiceAppService {

    /**
     * 注册服务应用
     * 
     * @param appName     应用名称
     * @param allowedApis 允许访问的接口列表
     * @param createBy    创建者
     * @param remark      备注
     * @return 注册成功的服务应用实体
     */
    ServiceAppEntity registerApp(String appName, List<String> allowedApis, String createBy, String remark);

    /**
     * 根据ID查询服务应用
     * 
     * @param id 主键ID
     * @return 服务应用实体
     */
    ServiceAppEntity getById(Long id);

    /**
     * 根据appId查询服务应用
     * 
     * @param appId 应用ID
     * @return 服务应用实体
     */
    ServiceAppEntity getByAppId(String appId);

    /**
     * 查询所有服务应用
     * 
     * @return 服务应用列表
     */
    List<ServiceAppEntity> getAllApps();

    /**
     * 查询所有启用状态的服务应用
     * 
     * @return 服务应用列表
     */
    List<ServiceAppEntity> getAllEnabledApps();

    /**
     * 更新服务应用
     * 
     * @param serviceApp 服务应用实体
     * @return 更新后的服务应用实体
     */
    ServiceAppEntity updateApp(ServiceAppEntity serviceApp);

    /**
     * 启用/禁用服务应用
     * 
     * @param id     主键ID
     * @param status 状态：1-启用，0-禁用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 删除服务应用
     * 
     * @param id 主键ID
     */
    void deleteApp(Long id);

    /**
     * 验证应用
     * 
     * @param appId    应用ID
     * @param authCode 授权码
     * @return 验证成功的服务应用实体，失败返回null
     */
    ServiceAppEntity validateApp(String appId, String authCode);

    /**
     * 检查应用是否有权限访问指定接口
     * 
     * @param appId   应用ID
     * @param apiPath 接口路径
     * @return true-有权限，false-无权限
     */
    boolean hasPermission(String appId, String apiPath);
}
