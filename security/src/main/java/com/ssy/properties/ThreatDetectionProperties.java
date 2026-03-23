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
     * 是否启用设备信誉评分
     */
    private boolean deviceRiskEnabled = true;

    /**
     * 设备信誉分达到该阈值时要求图片验证码校验
     */
    private int deviceRiskCaptchaScoreThreshold = 45;

    /**
     * 设备信誉分达到该阈值时拒绝登录
     */
    private int deviceRiskBlockScoreThreshold = 90;

    /**
     * 新设备首次访问附加分
     */
    private int deviceRiskNewDeviceScore = 8;

    /**
     * 同一设备切换 IP 的附加分
     */
    private int deviceRiskIpDriftScore = 12;

    /**
     * 同一设备切换 UA 家族的附加分
     */
    private int deviceRiskUaDriftScore = 10;

    /**
     * 同一设备短时间切换多个账号的附加分
     */
    private int deviceRiskMultiAccountScore = 18;

    /**
     * 单次登录失败的附加分
     */
    private int deviceRiskFailurePenalty = 8;

    /**
     * 统计设备切换账号的时间窗口
     */
    private long deviceRiskAccountSwitchWindowMs = 900_000L;

    /**
     * 设备切换账号阈值
     */
    private int deviceRiskAccountSwitchThreshold = 2;

    /**
     * 是否对私有 IP 段（10.x / 172.16-31.x / 192.168.x）的 IP 漂移减半计分。
     * 内网 DHCP 重新分配 IP 是正常现象，不应等同于外网代理切换。
     */
    private boolean deviceRiskTrustPrivateIpDrift = true;

    /**
     * 设备连续成功登录达到该次数后，开始累计信任加成（降低分数）。
     */
    private int deviceRiskTrustConsecutiveSuccessThreshold = 3;

    /**
     * 每达到一次连续成功登录阈值时的风险分减少量（负向调整）。
     * 例如：连续 3 次成功 → -10，连续 6 次成功 → -20，上限由 deviceRiskTrustMaxReduction 控制。
     */
    private int deviceRiskTrustSuccessReduction = 10;

    /**
     * 信任加成的最大减分上限，防止分数过低失去防护意义。
     */
    private int deviceRiskTrustMaxReduction = 30;

    /**
     * 设备评分时间衰减的半衰期（小时）。
     * 超过此时间后，IP 漂移、多账号切换等历史记录的影响折半。
     * 默认 168 小时（7 天），即一周后历史风险影响减半。
     */
    private int deviceRiskDecayHalfLifeHours = 168;

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
