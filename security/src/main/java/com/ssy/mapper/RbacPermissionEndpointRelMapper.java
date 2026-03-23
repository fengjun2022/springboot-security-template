package com.ssy.mapper;

import com.ssy.entity.RbacPermissionEndpointRelEntity;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RbacPermissionEndpointRelMapper {

    @Select("SELECT * FROM sys_permission_endpoint_rel WHERE endpoint_id = #{endpointId} AND status = 1 ORDER BY permission_id")
    List<RbacPermissionEndpointRelEntity> selectActiveByEndpointId(@Param("endpointId") Long endpointId);

    @Select("SELECT p.* FROM sys_permission p " +
            "JOIN sys_permission_endpoint_rel rel ON rel.permission_id = p.id " +
            "WHERE rel.endpoint_id = #{endpointId} AND rel.status = 1 AND p.status = 1 " +
            "ORDER BY p.id")
    List<com.ssy.entity.RbacPermissionEntity> selectEnabledPermissionsByEndpointId(@Param("endpointId") Long endpointId);

    @Select("SELECT rel.endpoint_id AS endpointId, p.perm_code AS permCode " +
            "FROM sys_permission_endpoint_rel rel " +
            "JOIN sys_permission p ON p.id = rel.permission_id " +
            "WHERE rel.status = 1 AND p.status = 1")
    List<EndpointPermissionCodeRow> selectAllActiveEndpointPermissionCodes();

    @Select("SELECT rel.endpoint_id AS endpointId, p.perm_code AS permCode, " +
            "rel.status AS relStatus, p.status AS permStatus " +
            "FROM sys_permission_endpoint_rel rel " +
            "JOIN sys_permission p ON p.id = rel.permission_id")
    List<EndpointPermissionBindingRow> selectAllEndpointPermissionBindings();

    @Delete("DELETE FROM sys_permission_endpoint_rel WHERE endpoint_id = #{endpointId}")
    int deleteByEndpointId(@Param("endpointId") Long endpointId);

    @Delete("DELETE FROM sys_permission_endpoint_rel WHERE permission_id = #{permissionId}")
    int deleteByPermissionId(@Param("permissionId") Long permissionId);

    @Select("SELECT e.* FROM api_endpoints e " +
            "JOIN sys_permission_endpoint_rel rel ON rel.endpoint_id = e.id " +
            "WHERE rel.permission_id = #{permissionId} AND rel.status = 1 " +
            "ORDER BY e.controller_class, e.path")
    List<com.ssy.entity.ApiEndpointEntity> selectEndpointsByPermissionId(@Param("permissionId") Long permissionId);

    @Insert("<script>" +
            "INSERT IGNORE INTO sys_permission_endpoint_rel (permission_id, endpoint_id, status, remark, create_time, update_time) VALUES " +
            "<foreach collection='permissionIds' item='permissionId' separator=','>" +
            "(#{permissionId}, #{endpointId}, 1, #{remark}, #{now}, #{now})" +
            "</foreach>" +
            "</script>")
    int insertEndpointPermissions(@Param("endpointId") Long endpointId,
                                  @Param("permissionIds") List<Long> permissionIds,
                                  @Param("remark") String remark,
                                  @Param("now") LocalDateTime now);

    @Insert("<script>" +
            "INSERT IGNORE INTO sys_permission_endpoint_rel (permission_id, endpoint_id, status, remark, create_time, update_time) VALUES " +
            "<foreach collection='endpointIds' item='endpointId' separator=','>" +
            "(#{permissionId}, #{endpointId}, 1, #{remark}, #{now}, #{now})" +
            "</foreach>" +
            "</script>")
    int insertPermissionEndpoints(@Param("permissionId") Long permissionId,
                                  @Param("endpointIds") List<Long> endpointIds,
                                  @Param("remark") String remark,
                                  @Param("now") LocalDateTime now);

    @Update("UPDATE sys_permission_endpoint_rel SET status = #{status}, update_time = #{updateTime} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("updateTime") LocalDateTime updateTime);

    class EndpointPermissionCodeRow {
        private Long endpointId;
        private String permCode;

        public Long getEndpointId() {
            return endpointId;
        }

        public void setEndpointId(Long endpointId) {
            this.endpointId = endpointId;
        }

        public String getPermCode() {
            return permCode;
        }

        public void setPermCode(String permCode) {
            this.permCode = permCode;
        }
    }

    class EndpointPermissionBindingRow {
        private Long endpointId;
        private String permCode;
        private Integer relStatus;
        private Integer permStatus;

        public Long getEndpointId() {
            return endpointId;
        }

        public void setEndpointId(Long endpointId) {
            this.endpointId = endpointId;
        }

        public String getPermCode() {
            return permCode;
        }

        public void setPermCode(String permCode) {
            this.permCode = permCode;
        }

        public Integer getRelStatus() {
            return relStatus;
        }

        public void setRelStatus(Integer relStatus) {
            this.relStatus = relStatus;
        }

        public Integer getPermStatus() {
            return permStatus;
        }

        public void setPermStatus(Integer permStatus) {
            this.permStatus = permStatus;
        }
    }
}
