package com.ssy.mapper;

import com.ssy.annotation.AutoGenerateSnowflakeId;
import com.ssy.entity.ServiceAppEntity;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 服务应用Mapper接口
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Repository
public interface ServiceAppMapper {

    /**
     * 插入服务应用记录
     * 
     * @param serviceApp 服务应用实体
     */
    @AutoGenerateSnowflakeId
    @Insert("INSERT INTO service_app (id, app_name, app_id, auth_code, allowed_apis, status, create_time, update_time, create_by, remark) "
            +
            "VALUES (#{id}, #{appName}, #{appId}, #{authCode}, #{allowedApis}, #{status}, #{createTime}, #{updateTime}, #{createBy}, #{remark})")
    void insert(ServiceAppEntity serviceApp);

    /**
     * 根据ID查询服务应用
     * 
     * @param id 主键ID
     * @return 服务应用实体
     */
    @Select("SELECT * FROM service_app WHERE id = #{id}")
    ServiceAppEntity selectById(Long id);

    /**
     * 根据appId查询服务应用
     * 
     * @param appId 应用ID
     * @return 服务应用实体
     */
    @Select("SELECT * FROM service_app WHERE app_id = #{appId}")
    ServiceAppEntity selectByAppId(String appId);

    /**
     * 根据应用名称查询服务应用
     * 
     * @param appName 应用名称
     * @return 服务应用实体
     */
    @Select("SELECT * FROM service_app WHERE app_name = #{appName}")
    ServiceAppEntity selectByAppName(String appName);

    /**
     * 查询所有启用状态的服务应用
     * 
     * @return 服务应用列表
     */
    @Select("SELECT * FROM service_app WHERE status = 1")
    List<ServiceAppEntity> selectAllEnabled();

    /**
     * 查询所有服务应用
     * 
     * @return 服务应用列表
     */
    @Select("SELECT * FROM service_app ORDER BY create_time DESC")
    List<ServiceAppEntity> selectAll();

    /**
     * 更新服务应用
     * 
     * @param serviceApp 服务应用实体
     */
    @Update("UPDATE service_app SET app_name = #{appName}, allowed_apis = #{allowedApis}, " +
            "status = #{status}, update_time = #{updateTime}, update_by = #{updateBy}, remark = #{remark} " +
            "WHERE id = #{id}")
    void update(ServiceAppEntity serviceApp);

    /**
     * 更新应用状态
     * 
     * @param id     主键ID
     * @param status 状态
     */
    @Update("UPDATE service_app SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 删除服务应用
     * 
     * @param id 主键ID
     */
    @Delete("DELETE FROM service_app WHERE id = #{id}")
    void deleteById(Long id);

    /**
     * 根据appId和authCode验证应用
     * 
     * @param appId    应用ID
     * @param authCode 授权码
     * @return 服务应用实体
     */
    @Select("SELECT * FROM service_app WHERE app_id = #{appId} AND auth_code = #{authCode} AND status = 1")
    ServiceAppEntity validateApp(@Param("appId") String appId, @Param("authCode") String authCode);
}
