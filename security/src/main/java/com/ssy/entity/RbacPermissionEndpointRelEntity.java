package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RbacPermissionEndpointRelEntity {
    private Long id;
    private Long permissionId;
    private Long endpointId;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
