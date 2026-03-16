package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SecurityIpBlacklistEntity {
    private Long id;
    private String ip;
    private Integer status;
    private String source;
    private String reason;
    private String attackType;
    private Integer hitCount;
    private LocalDateTime firstHitTime;
    private LocalDateTime lastHitTime;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;
}
