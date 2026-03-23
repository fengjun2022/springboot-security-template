package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SecurityAttackEventEntity {
    private Long id;
    private String ip;
    private String country;
    private String regionName;
    private String city;
    private String isp;
    private String locationLabel;
    private String attackType;
    private String attackTypeLabel;
    private String path;
    private String method;
    private Long endpointId;
    private String username;
    private String appId;
    private String clientTool;
    private String browserFingerprint;
    private Integer browserTrusted;
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
