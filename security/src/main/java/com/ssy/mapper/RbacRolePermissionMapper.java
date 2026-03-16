package com.ssy.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RbacRolePermissionMapper {

    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId} ORDER BY permission_id")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("<script>" +
            "INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES " +
            "<foreach collection='permissionIds' item='permissionId' separator=','>" +
            "(#{roleId}, #{permissionId})" +
            "</foreach>" +
            "</script>")
    int insertRolePermissions(@Param("roleId") Long roleId,
                              @Param("permissionIds") List<Long> permissionIds);
}
