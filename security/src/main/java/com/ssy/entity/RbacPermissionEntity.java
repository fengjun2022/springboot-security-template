package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RbacPermissionEntity {
    private Long id;
    private String permCode;
    private String permName;
    private String moduleGroup;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
