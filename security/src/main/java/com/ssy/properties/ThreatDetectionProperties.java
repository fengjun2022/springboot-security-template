package com.ssy.properties;

import com.ssy.factory.YamlPropertySourceFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Data
@Component
@PropertySource(value = "classpath:security.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "security.threat-detection")
public class ThreatDetectionProperties {

    /**
     * 总开关
     */
    private boolean enabled = true;

    /**
     * 未在api_endpoints中命中的路径是否默认纳入检测（建议开启，可识别扫描类请求）
     */
    private boolean monitorUnknownEndpoints = true;

    /**
     * 是否信任 X-Forwarded-For / X-Real-IP
     */
    private boolean trustForwardHeaders = true;

    /**
     * 是否抓取小请求体样本用于识别与审计
     */
    private boolean captureBodySample = true;

    /**
     * 最大检查/记录请求体大小（字节）
     */
    private int maxInspectBodyBytes = 4096;

    /**
     * 全局IP固定窗口限流配置
     */
    private long globalWindowMs = 10_000L;
    private int globalWindowLimit = 300;

    /**
     * 单接口固定窗口限流配置（监控接口生效）
     */
    private long endpointWindowMs = 10_000L;
    private int endpointWindowLimit = 120;

    /**
     * 触发自动拉黑时长（秒）
     */
    private int autoBlockSeconds = 600;

    /**
     * 若单窗口超过阈值倍数，直接拉黑（而不是仅限流）
     */
    private int autoBlockMultiplier = 3;

    /**
     * 401/403 结果回流统计窗口（毫秒）
     */
    private long authFeedbackWindowMs = 60_000L;

    /**
     * 同一IP+接口在窗口内401次数超过该值，记录可疑认证探测事件
     */
    private int auth401FeedbackThreshold = 8;

    /**
     * 同一IP+接口在窗口内403次数超过该值，记录越权探测事件
     */
    private int auth403FeedbackThreshold = 5;

    /**
     * 同一IP+接口在窗口内403次数超过该值，自动拉黑
     */
    private int auth403AutoBlockThreshold = 12;

    /**
     * 异步事件落库队列容量
     */
    private int eventQueueCapacity = 4096;

    /**
     * 黑名单持久化异步队列容量
     */
    private int blacklistQueueCapacity = 1024;

    /**
     * 静态扫描探测关键字（路径中命中即认为是扫描/探测）
     */
    private List<String> scannerPathKeywords = Arrays.asList(
            ".env",
            "phpmyadmin",
            "wp-admin",
            "wp-login",
            "actuator",
            "jmx-console",
            "swagger-ui",
            "manage",
            "druid"
    );
}
