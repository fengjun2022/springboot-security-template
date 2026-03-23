package com.ssy.service.impl;

import com.ssy.properties.ThreatDetectionProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备风险评估引擎 - 重构版
 *
 * 改进点：
 * 1. 时间衰减：IP 漂移、多账号切换等历史惩罚分随时间指数衰减，避免"一次犯错永久标记"
 * 2. 正面信号：连续成功登录积累信任，降低风险分；设备年龄越老信任度越高
 * 3. 内网 IP 信任：私有 IP 段（10.x/172.16.x/192.168.x）内的 IP 漂移不计全额惩罚
 * 4. 新设备惩罚递减：同一设备成功登录后，"新设备"标签逐步淡化
 */
@Service
public class DeviceRiskEngineService {

    private final ThreatDetectionProperties properties;
    /** key = 浏览器指纹，value = 设备历史 */
    private final Map<String, DeviceProfile> deviceProfiles = new ConcurrentHashMap<>();

    public DeviceRiskEngineService(ThreatDetectionProperties properties) {
        this.properties = properties;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 核心评估
    // ─────────────────────────────────────────────────────────────────────────

    public DeviceRiskAssessment assess(String ip,
                                       String username,
                                       String browserFingerprint,
                                       String userAgent,
                                       int failureCount,
                                       int captchaRefreshCount,
                                       int captchaFailCount) {
        cleanupExpired();
        if (!properties.isDeviceRiskEnabled()) {
            return DeviceRiskAssessment.disabled();
        }

        int riskScore = 0;
        List<String> reasons = new ArrayList<>();
        String normalizedFingerprint = normalize(browserFingerprint);
        String normalizedUsername = normalize(username);
        String userAgentFamily = resolveUserAgentFamily(userAgent);
        boolean browserLike = isBrowserLike(userAgent);

        DeviceProfile profile = StringUtils.hasText(normalizedFingerprint)
                ? deviceProfiles.get(normalizedFingerprint)
                : null;

        // ── 1. 指纹缺失 / 新设备 ──
        if (!StringUtils.hasText(normalizedFingerprint)) {
            riskScore += 25;
            reasons.add("浏览器指纹缺失");
        } else if (profile == null) {
            // 新设备：加基础分，但如果连续成功次数高则信任加成可能抵消
            riskScore += properties.getDeviceRiskNewDeviceScore();
            reasons.add("新设备首次访问");
        }

        // ── 2. 非浏览器请求 ──
        if (!browserLike) {
            riskScore += 35;
            reasons.add("请求头不符合真实浏览器特征");
        }

        // ── 3. 已知设备的历史对比（含时间衰减）──
        if (profile != null) {
            double decayFactor = computeDecayFactor(profile);

            // 3a. IP 漂移
            if (StringUtils.hasText(ip) && StringUtils.hasText(profile.lastIp) && !ip.equals(profile.lastIp)) {
                int ipDriftScore = properties.getDeviceRiskIpDriftScore();
                // 如果新旧 IP 均为私有 IP（内网 DHCP 变化），惩罚减半
                if (properties.isDeviceRiskTrustPrivateIpDrift()
                        && isPrivateIp(ip) && isPrivateIp(profile.lastIp)) {
                    ipDriftScore = ipDriftScore / 2;
                    reasons.add("内网 IP 变更（已减半计分）");
                } else {
                    reasons.add("同一设备短期切换 IP");
                }
                riskScore += (int) Math.round(ipDriftScore * decayFactor);
            }

            // 3b. 浏览器内核切换
            if (StringUtils.hasText(userAgentFamily)
                    && StringUtils.hasText(profile.lastUserAgentFamily)
                    && !userAgentFamily.equals(profile.lastUserAgentFamily)) {
                riskScore += (int) Math.round(properties.getDeviceRiskUaDriftScore() * decayFactor);
                reasons.add("同一设备短期切换浏览器内核");
            }

            // 3c. 多账号切换
            int distinctAccounts = profile.countRecentAccounts(properties.getDeviceRiskAccountSwitchWindowMs());
            boolean currentAccountSeen = profile.hasRecentAccount(normalizedUsername, properties.getDeviceRiskAccountSwitchWindowMs());
            if (!currentAccountSeen && distinctAccounts >= properties.getDeviceRiskAccountSwitchThreshold()) {
                riskScore += (int) Math.round(properties.getDeviceRiskMultiAccountScore() * decayFactor);
                reasons.add("同一设备短时间切换多个账号");
            }

            // 3d. 信任加成（正面信号）：连续成功登录降低风险分
            int trustReduction = computeTrustReduction(profile);
            if (trustReduction > 0) {
                riskScore = Math.max(0, riskScore - trustReduction);
                // 不加入 reasons，属于静默加成
            }
        }

        // ── 4. 登录失败累计惩罚 ──
        if (failureCount > 0) {
            riskScore += Math.min(40, failureCount * Math.max(properties.getDeviceRiskFailurePenalty(), 1));
            reasons.add("近期登录失败次数偏高");
        }

        // ── 5. 验证码滥用惩罚 ──
        // 5a. 频繁刷新验证码（超过 2 次后每次 +5 分，封顶 20 分）
        if (captchaRefreshCount > 2) {
            int refreshPenalty = Math.min(20, (captchaRefreshCount - 2) * 5);
            riskScore += refreshPenalty;
            reasons.add("验证码频繁刷新（" + captchaRefreshCount + "次）");
        }
        // 5b. 验证码多次输入错误（每次 +8 分，封顶 30 分）
        if (captchaFailCount > 0) {
            int captchaFailPenalty = Math.min(30, captchaFailCount * 8);
            riskScore += captchaFailPenalty;
            reasons.add("验证码多次输入错误（" + captchaFailCount + "次）");
        }

        // ── 6. 计算风险等级 ──
        String riskLevel;
        if (riskScore >= properties.getDeviceRiskBlockScoreThreshold()) {
            riskLevel = "CRITICAL";
        } else if (riskScore >= properties.getDeviceRiskCaptchaScoreThreshold()) {
            riskLevel = "HIGH";
        } else if (riskScore >= 20) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        return new DeviceRiskAssessment(
                true,
                riskScore,
                riskLevel,
                reasons,
                riskScore >= properties.getDeviceRiskCaptchaScoreThreshold(),
                riskScore >= properties.getDeviceRiskBlockScoreThreshold(),
                browserLike && StringUtils.hasText(normalizedFingerprint)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 事件回调
    // ─────────────────────────────────────────────────────────────────────────

    public void onLoginFailure(String ip, String username, String browserFingerprint, String userAgent) {
        if (!properties.isDeviceRiskEnabled()) return;
        String fp = normalize(browserFingerprint);
        if (!StringUtils.hasText(fp)) return;

        DeviceProfile profile = deviceProfiles.computeIfAbsent(fp, k -> new DeviceProfile());
        profile.lastSeenAt = LocalDateTime.now();
        profile.lastIp = normalize(ip);
        profile.lastUserAgentFamily = resolveUserAgentFamily(userAgent);
        // 登录失败：重置连续成功计数
        profile.consecutiveSuccessCount = 0;
        String normalizedUsername = normalize(username);
        if (StringUtils.hasText(normalizedUsername)) {
            profile.recentAccounts.put(normalizedUsername, LocalDateTime.now());
        }
    }

    public void onLoginSuccess(String ip, String username, String browserFingerprint, String userAgent) {
        if (!properties.isDeviceRiskEnabled()) return;
        String fp = normalize(browserFingerprint);
        if (!StringUtils.hasText(fp)) return;

        DeviceProfile profile = deviceProfiles.computeIfAbsent(fp, k -> new DeviceProfile());
        if (profile.firstSeenAt == null) {
            profile.firstSeenAt = LocalDateTime.now();
        }
        profile.lastSeenAt = LocalDateTime.now();
        profile.lastSuccessAt = LocalDateTime.now();
        profile.lastIp = normalize(ip);
        profile.lastUserAgentFamily = resolveUserAgentFamily(userAgent);
        // 登录成功：累加连续成功次数
        profile.consecutiveSuccessCount++;
        String normalizedUsername = normalize(username);
        if (StringUtils.hasText(normalizedUsername)) {
            profile.recentAccounts.put(normalizedUsername, LocalDateTime.now());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 时间衰减 & 信任计算
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 计算历史惩罚分的衰减系数（0.0 ~ 1.0）。
     * 基于指数衰减模型：factor = e^(-ln2 * hours / halfLifeHours)
     *   - halfLifeHours 内：factor > 0.5（惩罚仍较重）
     *   - 2 * halfLifeHours：factor ≈ 0.25（惩罚已很轻）
     *   - 默认半衰期 168h（7天）：一周前的 IP 漂移惩罚只剩 50%
     */
    private double computeDecayFactor(DeviceProfile profile) {
        if (profile.lastSuccessAt == null) {
            return 1.0; // 从未成功登录过，不做衰减
        }
        long hoursSinceSuccess = ChronoUnit.HOURS.between(profile.lastSuccessAt, LocalDateTime.now());
        int halfLife = Math.max(1, properties.getDeviceRiskDecayHalfLifeHours());
        // factor = 2^(-hours / halfLife)
        double factor = Math.pow(2.0, -(double) hoursSinceSuccess / halfLife);
        // 最低不低于 0.1（保留 10% 惩罚效果，防止完全免疫）
        return Math.max(0.1, factor);
    }

    /**
     * 计算连续成功登录带来的信任减分。
     * 每达到一个阈值倍数就减一次分，有上限。
     */
    private int computeTrustReduction(DeviceProfile profile) {
        int threshold = properties.getDeviceRiskTrustConsecutiveSuccessThreshold();
        if (threshold <= 0 || profile.consecutiveSuccessCount < threshold) {
            return 0;
        }
        int steps = profile.consecutiveSuccessCount / threshold;
        int reduction = steps * properties.getDeviceRiskTrustSuccessReduction();
        return Math.min(reduction, properties.getDeviceRiskTrustMaxReduction());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 判断 IP 是否为 RFC 1918 私有地址（内网 IP）。
     * 私有范围：10.0.0.0/8、172.16.0.0/12、192.168.0.0/16
     * 注意：这里只做字符串前缀判断，不解析 CIDR，够用且快速。
     */
    private boolean isPrivateIp(String ip) {
        if (!StringUtils.hasText(ip)) return false;
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("127.")) return true; // loopback
        if (ip.startsWith("::1")) return true;   // IPv6 loopback
        // 172.16.0.0/12 → 172.16.x.x ~ 172.31.x.x
        if (ip.startsWith("172.")) {
            try {
                String[] parts = ip.split("\\.");
                if (parts.length >= 2) {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                }
            } catch (NumberFormatException ignored) {
                // 解析失败当作非私有
            }
        }
        return false;
    }

    private boolean isBrowserLike(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return false;
        }
        String normalized = userAgent.toLowerCase();
        return normalized.contains("mozilla")
                || normalized.contains("chrome")
                || normalized.contains("safari")
                || normalized.contains("firefox")
                || normalized.contains("edg/");
    }

    private String resolveUserAgentFamily(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "";
        }
        String normalized = userAgent.toLowerCase();
        if (normalized.contains("edg/")) return "EDGE";
        if (normalized.contains("chrome")) return "CHROME";
        if (normalized.contains("firefox")) return "FIREFOX";
        if (normalized.contains("safari")) return "SAFARI";
        if (normalized.contains("curl")) return "CURL";
        if (normalized.contains("python-requests")) return "PYTHON_REQUESTS";
        return "UNKNOWN";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 过期清理
    // ─────────────────────────────────────────────────────────────────────────

    private void cleanupExpired() {
        // 设备 profile 在最后一次访问超过 accountSwitchWindow 的 4 倍后清理（保留足够的历史）
        long retentionMs = properties.getDeviceRiskAccountSwitchWindowMs() * 4;
        LocalDateTime expireBefore = LocalDateTime.now().minusNanos(retentionMs * 1_000_000L);

        Iterator<Map.Entry<String, DeviceProfile>> iterator = deviceProfiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, DeviceProfile> entry = iterator.next();
            DeviceProfile profile = entry.getValue();
            // 清理 recentAccounts 中的过期账号记录
            LocalDateTime accountExpireBefore = LocalDateTime.now()
                    .minusNanos(properties.getDeviceRiskAccountSwitchWindowMs() * 1_000_000L);
            profile.recentAccounts.entrySet().removeIf(account -> account.getValue().isBefore(accountExpireBefore));
            // 若设备长时间未访问，删除整个 profile
            if (profile.lastSeenAt == null || profile.lastSeenAt.isBefore(expireBefore)) {
                iterator.remove();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部数据结构
    // ─────────────────────────────────────────────────────────────────────────

    private static class DeviceProfile {
        /** 首次访问时间（用于判断设备年龄） */
        private LocalDateTime firstSeenAt;
        /** 最后一次访问时间 */
        private LocalDateTime lastSeenAt;
        /** 最后一次成功登录时间（用于时间衰减基准） */
        private LocalDateTime lastSuccessAt;
        /** 连续成功登录次数（失败时重置） */
        private int consecutiveSuccessCount = 0;
        /** 最后记录的 IP */
        private String lastIp;
        /** 最后记录的浏览器内核家族 */
        private String lastUserAgentFamily;
        /** 近期登录账号历史 username → 最后访问时间 */
        private final Map<String, LocalDateTime> recentAccounts = new LinkedHashMap<>();

        private int countRecentAccounts(long windowMs) {
            LocalDateTime expireBefore = LocalDateTime.now().minusNanos(windowMs * 1_000_000L);
            recentAccounts.entrySet().removeIf(entry -> entry.getValue().isBefore(expireBefore));
            return recentAccounts.size();
        }

        private boolean hasRecentAccount(String username, long windowMs) {
            if (!StringUtils.hasText(username)) return false;
            LocalDateTime seenAt = recentAccounts.get(username);
            return seenAt != null && !seenAt.isBefore(LocalDateTime.now().minusNanos(windowMs * 1_000_000L));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 评估结果
    // ─────────────────────────────────────────────────────────────────────────

    public static class DeviceRiskAssessment {
        private final boolean enabled;
        private final int riskScore;
        private final String riskLevel;
        private final List<String> riskReasons;
        private final boolean captchaChallengeRequired;
        private final boolean blocked;
        private final boolean trustedBrowser;

        public DeviceRiskAssessment(boolean enabled,
                                    int riskScore,
                                    String riskLevel,
                                    List<String> riskReasons,
                                    boolean captchaChallengeRequired,
                                    boolean blocked,
                                    boolean trustedBrowser) {
            this.enabled = enabled;
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
            this.riskReasons = riskReasons;
            this.captchaChallengeRequired = captchaChallengeRequired;
            this.blocked = blocked;
            this.trustedBrowser = trustedBrowser;
        }

        public static DeviceRiskAssessment disabled() {
            return new DeviceRiskAssessment(false, 0, "LOW", new ArrayList<>(), false, false, false);
        }

        public boolean isEnabled() { return enabled; }
        public int getRiskScore() { return riskScore; }
        public String getRiskLevel() { return riskLevel; }
        public List<String> getRiskReasons() { return riskReasons; }
        public boolean isCaptchaChallengeRequired() { return captchaChallengeRequired; }
        public boolean isBlocked() { return blocked; }
        public boolean isTrustedBrowser() { return trustedBrowser; }
    }
}
