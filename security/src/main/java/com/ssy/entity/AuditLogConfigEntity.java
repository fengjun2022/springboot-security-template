package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogConfigEntity {
    private Long id;
    private Integer enabled;
    private Integer retentionDays;
    private Long maxRowsPerTable;
    private Integer queueCapacity;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
