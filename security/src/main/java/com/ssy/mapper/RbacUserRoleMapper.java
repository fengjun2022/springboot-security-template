package com.ssy.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RbacUserRoleMapper {

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId} AND status = 1 ORDER BY role_id")
    List<Long> selectActiveRoleIdsByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Insert("<script>" +
            "INSERT IGNORE INTO sys_user_role (user_id, role_id, source, status) VALUES " +
            "<foreach collection='roleIds' item='roleId' separator=','>" +
            "(#{userId}, #{roleId}, #{source}, 1)" +
            "</foreach>" +
            "</script>")
    int insertUserRoles(@Param("userId") Long userId,
                        @Param("roleIds") List<Long> roleIds,
                        @Param("source") String source);

    @Insert("INSERT IGNORE INTO sys_user_role (user_id, role_id, source, status) VALUES (#{userId}, #{roleId}, #{source}, 1)")
    int insertOne(@Param("userId") Long userId, @Param("roleId") Long roleId, @Param("source") String source);
}
