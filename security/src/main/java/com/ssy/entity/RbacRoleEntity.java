package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RbacRoleEntity {
    private Long id;
    private String roleCode;
    private String roleName;
    private Integer status;
    private Integer isSystem;
    private Integer allowSelfRegister;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
