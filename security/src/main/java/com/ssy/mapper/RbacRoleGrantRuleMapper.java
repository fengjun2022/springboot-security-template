package com.ssy.mapper;

import com.ssy.entity.RbacRoleGrantRuleEntity;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RbacRoleGrantRuleMapper {

    @Select("SELECT gr.*, " +
            "orole.role_code AS operatorRoleCode, orole.role_name AS operatorRoleName, " +
            "trole.role_code AS targetRoleCode, trole.role_name AS targetRoleName " +
            "FROM sys_role_grant_rule gr " +
            "JOIN sys_role orole ON orole.id = gr.operator_role_id " +
            "JOIN sys_role trole ON trole.id = gr.target_role_id " +
            "ORDER BY gr.operator_role_id, gr.target_role_id")
    @Results(id = "rbacGrantRuleResultMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "operator_role_id", property = "operatorRoleId"),
            @Result(column = "target_role_id", property = "targetRoleId"),
            @Result(column = "can_create_user_with_role", property = "canCreateUserWithRole"),
            @Result(column = "can_assign_role", property = "canAssignRole"),
            @Result(column = "can_revoke_role", property = "canRevokeRole"),
            @Result(column = "can_update_user_of_role", property = "canUpdateUserOfRole"),
            @Result(column = "status", property = "status"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "operatorRoleCode", property = "operatorRoleCode"),
            @Result(column = "operatorRoleName", property = "operatorRoleName"),
            @Result(column = "targetRoleCode", property = "targetRoleCode"),
            @Result(column = "targetRoleName", property = "targetRoleName")
    })
    List<RbacRoleGrantRuleEntity> selectAllWithRoleNames();

    @Select("SELECT gr.*, " +
            "orole.role_code AS operatorRoleCode, orole.role_name AS operatorRoleName, " +
            "trole.role_code AS targetRoleCode, trole.role_name AS targetRoleName " +
            "FROM sys_role_grant_rule gr " +
            "JOIN sys_role orole ON orole.id = gr.operator_role_id " +
            "JOIN sys_role trole ON trole.id = gr.target_role_id " +
            "WHERE gr.status = 1 " +
            "ORDER BY gr.operator_role_id, gr.target_role_id")
    @ResultMap("rbacGrantRuleResultMap")
    List<RbacRoleGrantRuleEntity> selectEnabledWithRoleNames();

    @Insert("INSERT INTO sys_role_grant_rule (" +
            "operator_role_id, target_role_id, can_create_user_with_role, can_assign_role, can_revoke_role, can_update_user_of_role, status, remark, create_time, update_time" +
            ") VALUES (" +
            "#{operatorRoleId}, #{targetRoleId}, #{canCreateUserWithRole}, #{canAssignRole}, #{canRevokeRole}, #{canUpdateUserOfRole}, #{status}, #{remark}, #{createTime}, #{updateTime}" +
            ") ON DUPLICATE KEY UPDATE " +
            "can_create_user_with_role = VALUES(can_create_user_with_role), " +
            "can_assign_role = VALUES(can_assign_role), " +
            "can_revoke_role = VALUES(can_revoke_role), " +
            "can_update_user_of_role = VALUES(can_update_user_of_role), " +
            "status = VALUES(status), " +
            "remark = VALUES(remark), " +
            "update_time = VALUES(update_time)")
    int upsert(RbacRoleGrantRuleEntity entity);

    @Update("UPDATE sys_role_grant_rule SET status = #{status}, update_time = #{updateTime} " +
            "WHERE operator_role_id = #{operatorRoleId} AND target_role_id = #{targetRoleId}")
    int updateStatus(@Param("operatorRoleId") Long operatorRoleId,
                     @Param("targetRoleId") Long targetRoleId,
                     @Param("status") Integer status,
                     @Param("updateTime") LocalDateTime updateTime);
}
