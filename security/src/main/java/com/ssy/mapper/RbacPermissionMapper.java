package com.ssy.mapper;

import com.ssy.entity.RbacPermissionEntity;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RbacPermissionMapper {

    @Select("SELECT * FROM sys_permission WHERE status = 1 ORDER BY id")
    List<RbacPermissionEntity> selectEnabledList();

    @Select("SELECT * FROM sys_permission ORDER BY id")
    List<RbacPermissionEntity> selectAll();

    @Select("SELECT * FROM sys_permission WHERE id = #{id}")
    RbacPermissionEntity selectById(@Param("id") Long id);

    @Select("SELECT * FROM sys_permission WHERE perm_code = #{permCode}")
    RbacPermissionEntity selectByCode(@Param("permCode") String permCode);

    @Select("<script>" +
            "SELECT * FROM sys_permission WHERE perm_code IN " +
            "<foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach>" +
            " ORDER BY id" +
            "</script>")
    List<RbacPermissionEntity> selectByCodes(@Param("codes") List<String> codes);

    @Select("<script>" +
            "SELECT * FROM sys_permission WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " ORDER BY id" +
            "</script>")
    List<RbacPermissionEntity> selectByIds(@Param("ids") List<Long> ids);

    @Select("SELECT DISTINCT p.* FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
            "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND ur.status = 1 AND r.status = 1 AND p.status = 1 " +
            "ORDER BY p.id")
    List<RbacPermissionEntity> selectEnabledByUserId(@Param("userId") Long userId);

    @Select("SELECT p.* FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
            "WHERE rp.role_id = #{roleId} ORDER BY p.id")
    List<RbacPermissionEntity> selectByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_permission (perm_code, perm_name, module_group, status, remark, create_time, update_time) " +
            "VALUES (#{permCode}, #{permName}, #{moduleGroup}, #{status}, #{remark}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RbacPermissionEntity entity);

    @Update("<script>" +
            "UPDATE sys_permission <set>" +
            "<if test='permName != null'>perm_name = #{permName},</if>" +
            "<if test='moduleGroup != null'>module_group = #{moduleGroup},</if>" +
            "<if test='status != null'>status = #{status},</if>" +
            "<if test='remark != null'>remark = #{remark},</if>" +
            "update_time = #{updateTime}" +
            "</set> WHERE id = #{id}" +
            "</script>")
    int update(RbacPermissionEntity entity);

    @Update("UPDATE sys_permission SET status = #{status}, update_time = #{updateTime} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("updateTime") LocalDateTime updateTime);
}
