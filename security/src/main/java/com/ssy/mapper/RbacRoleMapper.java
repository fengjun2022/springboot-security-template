package com.ssy.mapper;

import com.ssy.entity.RbacRoleEntity;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RbacRoleMapper {

    @Select("SELECT * FROM sys_role WHERE status = 1 ORDER BY id")
    List<RbacRoleEntity> selectEnabledList();

    @Select("SELECT * FROM sys_role ORDER BY id")
    List<RbacRoleEntity> selectAll();

    @Select("SELECT * FROM sys_role WHERE id = #{id}")
    RbacRoleEntity selectById(@Param("id") Long id);

    @Select("SELECT * FROM sys_role WHERE role_code = #{roleCode}")
    RbacRoleEntity selectByCode(@Param("roleCode") String roleCode);

    @Select("<script>" +
            "SELECT * FROM sys_role WHERE role_code IN " +
            "<foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach>" +
            " ORDER BY id" +
            "</script>")
    List<RbacRoleEntity> selectByCodes(@Param("codes") List<String> codes);

    @Select("<script>" +
            "SELECT * FROM sys_role WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " ORDER BY id" +
            "</script>")
    List<RbacRoleEntity> selectByIds(@Param("ids") List<Long> ids);

    @Select("SELECT r.* FROM sys_role r " +
            "JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND ur.status = 1 AND r.status = 1 " +
            "ORDER BY r.id")
    List<RbacRoleEntity> selectEnabledByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_role (role_code, role_name, status, is_system, allow_self_register, remark, create_time, update_time) " +
            "VALUES (#{roleCode}, #{roleName}, #{status}, #{isSystem}, #{allowSelfRegister}, #{remark}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RbacRoleEntity entity);

    @Update("<script>" +
            "UPDATE sys_role <set>" +
            "<if test='roleName != null'>role_name = #{roleName},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "<if test='allowSelfRegister != null'>allow_self_register = #{allowSelfRegister},</if>" +
            "<if test='remark != null'>remark = #{remark},</if>" +
            "update_time = #{updateTime}" +
            "</set> WHERE id = #{id}" +
            "</script>")
    int update(RbacRoleEntity entity);

    @Update("UPDATE sys_role SET status = #{status}, update_time = #{updateTime} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("updateTime") LocalDateTime updateTime);
}
