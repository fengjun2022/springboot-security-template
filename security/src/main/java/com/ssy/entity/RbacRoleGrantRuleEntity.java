package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RbacRoleGrantRuleEntity {
    private Long id;
    private Long operatorRoleId;
    private Long targetRoleId;
    private Integer canCreateUserWithRole;
    private Integer canAssignRole;
    private Integer canRevokeRole;
    private Integer canUpdateUserOfRole;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // Convenience fields for joins/UI display
    private String operatorRoleCode;
    private String operatorRoleName;
    private String targetRoleCode;
    private String targetRoleName;
}
