package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SecurityAttackEventEntity {
    private Long id;
    private String ip;
    private String attackType;
    private String path;
    private String method;
    private Long endpointId;
    private String username;
    private String appId;
    private String userAgent;
    private String referer;
    private String queryString;
    private String requestBodySample;
    private String requestBodyHash;
    private Integer riskScore;
    private String blockAction;
    private String blockReason;
    private String suggestedAction;
    private LocalDateTime createTime;
}
