package com.ssy.mapper;

import com.ssy.annotation.AutoGenerateSnowflakeId;
import com.ssy.entity.ServiceTokenEntity;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务TokenMapper接口
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Repository
public interface ServiceTokenMapper {

    /**
     * 插入服务Token记录
     * 
     * @param serviceToken 服务Token实体
     */
    @AutoGenerateSnowflakeId
    @Insert("INSERT INTO service_token (id, app_id, token, token_type, status, issue_time, issue_by, remark) " +
            "VALUES (#{id}, #{appId}, #{token}, #{tokenType}, #{status}, #{issueTime}, #{issueBy}, #{remark})")
    void insert(ServiceTokenEntity serviceToken);

    /**
     * 根据token查询记录
     * 
     * @param token token值
     * @return 服务Token实体
     */
    @Select("SELECT * FROM service_token WHERE token = #{token} AND status = 1")
    ServiceTokenEntity selectByToken(String token);

    /**
     * 根据appId查询有效的token
     * 
     * @param appId 应用ID
     * @return 服务Token实体
     */
    @Select("SELECT * FROM service_token WHERE app_id = #{appId} AND status = 1 ORDER BY issue_time DESC LIMIT 1")
    ServiceTokenEntity selectByAppId(String appId);

    /**
     * 查询所有有效的token
     * 
     * @return 服务Token列表
     */
    @Select("SELECT * FROM service_token WHERE status = 1")
    List<ServiceTokenEntity> selectAllValid();

    /**
     * 更新token最后使用时间
     * 
     * @param token        token值
     * @param lastUsedTime 最后使用时间
     */
    @Update("UPDATE service_token SET last_used_time = #{lastUsedTime} WHERE token = #{token}")
    void updateLastUsedTime(@Param("token") String token, @Param("lastUsedTime") LocalDateTime lastUsedTime);

    /**
     * 更新token状态
     * 
     * @param id     主键ID
     * @param status 状态
     */
    @Update("UPDATE service_token SET status = #{status} WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 根据appId失效所有token
     * 
     * @param appId 应用ID
     */
    @Update("UPDATE service_token SET status = 0 WHERE app_id = #{appId}")
    void invalidateByAppId(String appId);

    /**
     * 删除token记录
     * 
     * @param id 主键ID
     */
    @Delete("DELETE FROM service_token WHERE id = #{id}")
    void deleteById(Long id);

    /**
     * 查询所有token
     * 
     * @return 服务Token列表
     */
    @Select("SELECT * FROM service_token ORDER BY issue_time DESC")
    List<ServiceTokenEntity> selectAll();
}
