package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogRecordEntity {
    private Long id;
    private String category;
    private String eventType;
    private String moduleName;
    private String operationName;
    private String resourceType;
    private String resourceId;
    private Integer success;
    private String detailText;
    private String requestMethod;
    private String requestUri;
    private String clientIp;
    private String username;
    private Long userId;
    private String loginType;
    private Integer responseCode;
    private String traceId;
    private String extJson;
    private String apiDescription;
    private LocalDateTime createTime;
}
