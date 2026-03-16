package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SecurityIpWhitelistEntity {
    private Long id;
    private String ipOrCidr;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
