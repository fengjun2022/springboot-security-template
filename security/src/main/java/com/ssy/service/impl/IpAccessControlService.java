package com.ssy.service.impl;

import com.ssy.entity.SecurityIpBlacklistEntity;
import com.ssy.entity.SecurityIpWhitelistEntity;
import com.ssy.mapper.SecurityIpBlacklistMapper;
import com.ssy.mapper.SecurityIpWhitelistMapper;
import com.ssy.properties.ThreatDetectionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * IP黑白名单 + 高频访问检测（本地内存热路径，异步持久化）。
 */
@Service
public class IpAccessControlService {

    private static final Logger log = LoggerFactory.getLogger(IpAccessControlService.class);

    private final SecurityIpBlacklistMapper securityIpBlacklistMapper;
    private final SecurityIpWhitelistMapper securityIpWhitelistMapper;
    private final ThreatDetectionProperties properties;

    /**
     * 黑名单缓存：ip -> 过期毫秒时间戳（Long.MAX_VALUE 表示永久）
     */
    private final ConcurrentHashMap<String, Long> blacklistCache = new ConcurrentHashMap<>();
    private final Set<String> whitelistCache = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<CidrWhitelistRule> whitelistCidrRules = new CopyOnWriteArrayList<>();

    /**
     * 固定窗口计数器
     */
    private final ConcurrentHashMap<String, FixedWindowCounter> globalCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FixedWindowCounter> endpointCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FixedWindowCounter> authFeedbackCounters = new ConcurrentHashMap<>();

    private final ExecutorService blacklistPersistenceExecutor;

    public IpAccessControlService(SecurityIpBlacklistMapper securityIpBlacklistMapper,
                                  SecurityIpWhitelistMapper securityIpWhitelistMapper,
                                  ThreatDetectionProperties properties) {
        this.securityIpBlacklistMapper = securityIpBlacklistMapper;
        this.securityIpWhitelistMapper = securityIpWhitelistMapper;
        this.properties = properties;
        this.blacklistPersistenceExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(64, properties.getBlacklistQueueCapacity())),
                r -> {
                    Thread t = new Thread(r, "ThreatBlacklistPersist");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    public void refreshCaches() {
        List<SecurityIpBlacklistEntity> activeBlackList = safeBlacklistLoad();
        List<SecurityIpWhitelistEntity> activeWhiteList = safeWhitelistLoad();

        blacklistCache.clear();
        whitelistCache.clear();
        whitelistCidrRules.clear();

        for (SecurityIpBlacklistEntity entity : activeBlackList) {
            if (entity.getIp() == null || entity.getIp().isEmpty()) {
                continue;
            }
            blacklistCache.put(entity.getIp(), toExpireEpochMillis(entity.getExpireTime()));
        }
        for (SecurityIpWhitelistEntity entity : activeWhiteList) {
            if (entity.getIpOrCidr() == null || entity.getIpOrCidr().isEmpty()) {
                continue;
            }
            String value = entity.getIpOrCidr().trim();
            if (value.contains("/")) {
                CidrWhitelistRule rule = CidrWhitelistRule.parse(value);
                if (rule != null) {
                    whitelistCidrRules.add(rule);
                } else {
                    log.warn("忽略非法CIDR白名单配置: {}", value);
                }
            } else {
                whitelistCache.add(value);
            }
        }

        log.info("IP黑白名单缓存已刷新: blacklist={}, whitelist={}", blacklistCache.size(), whitelistCache.size());
    }

    public boolean isWhitelisted(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if (whitelistCache.contains(ip)) {
            return true;
        }
        for (CidrWhitelistRule rule : whitelistCidrRules) {
            if (rule.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    public boolean isBlacklisted(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        Long expireAt = blacklistCache.get(ip);
        if (expireAt == null) {
            return false;
        }
        if (expireAt == Long.MAX_VALUE) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (expireAt > now) {
            return true;
        }
        blacklistCache.remove(ip, expireAt);
        return false;
    }

    public RateCheckResult checkRate(String ip, String endpointKey, boolean endpointMonitoringEnabled) {
        long now = System.currentTimeMillis();

        int globalCount = increment(globalCounters, "g:" + ip, now, properties.getGlobalWindowMs());
        if (globalCount > properties.getGlobalWindowLimit() * Math.max(1, properties.getAutoBlockMultiplier())) {
            return RateCheckResult.autoBlacklist("RATE_GLOBAL_BURST", "IP全局请求频率极端异常", globalCount);
        }
        if (globalCount > properties.getGlobalWindowLimit()) {
            return RateCheckResult.block("RATE_GLOBAL_LIMIT", "IP全局请求频率异常", globalCount);
        }

        if (endpointMonitoringEnabled && endpointKey != null) {
            int endpointCount = increment(endpointCounters, "e:" + ip + ":" + endpointKey, now, properties.getEndpointWindowMs());
            if (endpointCount > properties.getEndpointWindowLimit() * Math.max(1, properties.getAutoBlockMultiplier())) {
                return RateCheckResult.autoBlacklist("RATE_ENDPOINT_BURST", "单接口访问频率极端异常", endpointCount);
            }
            if (endpointCount > properties.getEndpointWindowLimit()) {
                return RateCheckResult.block("RATE_ENDPOINT_LIMIT", "单接口访问频率异常", endpointCount);
            }
        }

        return RateCheckResult.allow();
    }

    public AuthFeedbackResult recordAuthResultAndCheck(String ip, String endpointKey, int statusCode) {
        if (ip == null || ip.isEmpty() || endpointKey == null || endpointKey.isEmpty()) {
            return AuthFeedbackResult.noop();
        }
        if (statusCode != 401 && statusCode != 403) {
            return AuthFeedbackResult.noop();
        }

        long now = System.currentTimeMillis();
        String key = "auth:" + statusCode + ":" + ip + ":" + endpointKey;
        int count = increment(authFeedbackCounters, key, now, properties.getAuthFeedbackWindowMs());

        if (statusCode == 403) {
            if (count >= properties.getAuth403AutoBlockThreshold()) {
                return AuthFeedbackResult.alert(
                        "PRIVILEGE_PROBE_403",
                        "同一IP对接口短时大量403，疑似越权探测，计数=" + count,
                        "建议审计该IP的认证信息与访问轨迹，已触发自动拉黑",
                        true,
                        85,
                        count
                );
            }
            if (count >= properties.getAuth403FeedbackThreshold()) {
                return AuthFeedbackResult.alert(
                        "PRIVILEGE_PROBE_403",
                        "同一IP对接口短时重复403，疑似越权探测，计数=" + count,
                        "建议观察该IP是否持续探测敏感接口",
                        false,
                        70,
                        count
                );
            }
            return AuthFeedbackResult.noop();
        }

        if (count >= properties.getAuth401FeedbackThreshold()) {
            return AuthFeedbackResult.alert(
                    "AUTH_PROBE_401",
                    "同一IP对接口短时重复401，疑似认证探测，计数=" + count,
                    "建议排查是否为暴力探测或失效脚本重试",
                    false,
                    55,
                    count
            );
        }
        return AuthFeedbackResult.noop();
    }

    public void addToBlacklist(String ip, String attackType, String reason, int expireSeconds) {
        addToBlacklist(ip, attackType, reason, expireSeconds, "AUTO", "系统自动拉黑");
    }

    public void manualBlockIp(String ip, String reason, int expireSeconds) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        SecurityIpBlacklistEntity entity = buildBlacklistEntity(
                ip,
                "MANUAL_BLOCK",
                reason,
                expireSeconds,
                "MANUAL",
                "管理员手动拉黑"
        );
        blacklistCache.merge(ip, toExpireEpochMillis(entity.getExpireTime()), Math::max);
        try {
            securityIpBlacklistMapper.upsert(entity);
        } catch (Exception e) {
            blacklistCache.remove(ip);
            throw new IllegalStateException("手动拉黑持久化失败: " + e.getMessage(), e);
        }
    }

    public void removeFromBlacklist(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        blacklistCache.remove(ip);
        try {
            securityIpBlacklistMapper.disableByIp(ip);
        } catch (Exception e) {
            log.warn("解除黑名单失败 ip={}: {}", ip, e.getMessage());
        }
    }

    public void addToWhitelist(String ipOrCidr, String remark) {
        if (ipOrCidr == null || ipOrCidr.trim().isEmpty()) {
            return;
        }
        String value = ipOrCidr.trim();
        try {
            securityIpWhitelistMapper.upsert(value, remark);
            refreshCaches();
        } catch (Exception e) {
            log.warn("添加白名单失败 value={}: {}", value, e.getMessage());
        }
    }

    public void removeFromWhitelist(String ipOrCidr) {
        if (ipOrCidr == null || ipOrCidr.trim().isEmpty()) {
            return;
        }
        String value = ipOrCidr.trim();
        try {
            securityIpWhitelistMapper.disableByIpOrCidr(value);
            refreshCaches();
        } catch (Exception e) {
            log.warn("移除白名单失败 value={}: {}", value, e.getMessage());
        }
    }

    private void addToBlacklist(String ip, String attackType, String reason, int expireSeconds, String source, String remark) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        SecurityIpBlacklistEntity entity = buildBlacklistEntity(ip, attackType, reason, expireSeconds, source, remark);
        long expireEpochMillis = toExpireEpochMillis(entity.getExpireTime());
        blacklistCache.merge(ip, expireEpochMillis, Math::max);

        blacklistPersistenceExecutor.execute(() -> {
            try {
                securityIpBlacklistMapper.upsert(entity);
            } catch (Exception e) {
                log.warn("异步持久化黑名单失败 ip={}, reason={}: {}", ip, reason, e.getMessage());
            }
        });
    }

    private SecurityIpBlacklistEntity buildBlacklistEntity(String ip,
                                                           String attackType,
                                                           String reason,
                                                           int expireSeconds,
                                                           String source,
                                                           String remark) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = expireSeconds <= 0 ? null : now.plusSeconds(expireSeconds);

        SecurityIpBlacklistEntity entity = new SecurityIpBlacklistEntity();
        entity.setIp(ip);
        entity.setStatus(1);
        entity.setSource(source);
        entity.setReason(reason);
        entity.setAttackType(attackType);
        entity.setHitCount(1);
        entity.setFirstHitTime(now);
        entity.setLastHitTime(now);
        entity.setExpireTime(expireTime);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setRemark(remark);
        return entity;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupLocalAndDbState() {
        long now = System.currentTimeMillis();

        blacklistCache.entrySet().removeIf(entry -> entry.getValue() != Long.MAX_VALUE && entry.getValue() <= now);

        long globalTtl = Math.max(properties.getGlobalWindowMs(), properties.getEndpointWindowMs()) * 3;
        globalCounters.entrySet().removeIf(entry -> entry.getValue().isIdle(now, globalTtl));
        endpointCounters.entrySet().removeIf(entry -> entry.getValue().isIdle(now, globalTtl));
        authFeedbackCounters.entrySet().removeIf(entry -> entry.getValue().isIdle(now, Math.max(globalTtl, properties.getAuthFeedbackWindowMs() * 3)));

        try {
            securityIpBlacklistMapper.disableExpired(LocalDateTime.now());
        } catch (Exception e) {
            log.debug("清理过期黑名单状态失败: {}", e.getMessage());
        }
    }

    public int blacklistSize() {
        return blacklistCache.size();
    }

    public int whitelistSize() {
        return whitelistCache.size() + whitelistCidrRules.size();
    }

    private int increment(Map<String, FixedWindowCounter> counters, String key, long now, long windowMs) {
        FixedWindowCounter counter = counters.computeIfAbsent(key, k -> new FixedWindowCounter());
        return counter.increment(now, windowMs);
    }

    private long toExpireEpochMillis(LocalDateTime expireTime) {
        if (expireTime == null) {
            return Long.MAX_VALUE;
        }
        return expireTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private List<SecurityIpBlacklistEntity> safeBlacklistLoad() {
        try {
            return securityIpBlacklistMapper.selectActiveList();
        } catch (Exception e) {
            log.warn("加载黑名单失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<SecurityIpWhitelistEntity> safeWhitelistLoad() {
        try {
            return securityIpWhitelistMapper.selectEnabledList();
        } catch (Exception e) {
            log.warn("加载白名单失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @PreDestroy
    public void destroy() {
        blacklistPersistenceExecutor.shutdown();
    }

    private static class FixedWindowCounter {
        private long windowStart;
        private int count;
        private long lastSeen;

        synchronized int increment(long now, long windowMs) {
            if (windowStart == 0 || now - windowStart >= windowMs) {
                windowStart = now;
                count = 0;
            }
            count++;
            lastSeen = now;
            return count;
        }

        synchronized boolean isIdle(long now, long idleMs) {
            return lastSeen > 0 && (now - lastSeen) > idleMs;
        }
    }

    public static class RateCheckResult {
        private final boolean allow;
        private final boolean shouldBlacklist;
        private final String attackType;
        private final String reason;
        private final int observedCount;

        private RateCheckResult(boolean allow, boolean shouldBlacklist, String attackType, String reason, int observedCount) {
            this.allow = allow;
            this.shouldBlacklist = shouldBlacklist;
            this.attackType = attackType;
            this.reason = reason;
            this.observedCount = observedCount;
        }

        public static RateCheckResult allow() {
            return new RateCheckResult(true, false, null, null, 0);
        }

        public static RateCheckResult block(String attackType, String reason, int observedCount) {
            return new RateCheckResult(false, false, attackType, reason, observedCount);
        }

        public static RateCheckResult autoBlacklist(String attackType, String reason, int observedCount) {
            return new RateCheckResult(false, true, attackType, reason, observedCount);
        }

        public boolean isAllow() {
            return allow;
        }

        public boolean isShouldBlacklist() {
            return shouldBlacklist;
        }

        public String getAttackType() {
            return attackType;
        }

        public String getReason() {
            return reason;
        }

        public int getObservedCount() {
            return observedCount;
        }
    }

    public static class AuthFeedbackResult {
        private final boolean shouldRecord;
        private final boolean shouldBlacklist;
        private final String attackType;
        private final String reason;
        private final String suggestedAction;
        private final int riskScore;
        private final int observedCount;

        private AuthFeedbackResult(boolean shouldRecord, boolean shouldBlacklist, String attackType,
                                   String reason, String suggestedAction, int riskScore, int observedCount) {
            this.shouldRecord = shouldRecord;
            this.shouldBlacklist = shouldBlacklist;
            this.attackType = attackType;
            this.reason = reason;
            this.suggestedAction = suggestedAction;
            this.riskScore = riskScore;
            this.observedCount = observedCount;
        }

        public static AuthFeedbackResult noop() {
            return new AuthFeedbackResult(false, false, null, null, null, 0, 0);
        }

        public static AuthFeedbackResult alert(String attackType, String reason, String suggestedAction,
                                               boolean shouldBlacklist, int riskScore, int observedCount) {
            return new AuthFeedbackResult(true, shouldBlacklist, attackType, reason, suggestedAction, riskScore, observedCount);
        }

        public boolean isShouldRecord() {
            return shouldRecord;
        }

        public boolean isShouldBlacklist() {
            return shouldBlacklist;
        }

        public String getAttackType() {
            return attackType;
        }

        public String getReason() {
            return reason;
        }

        public String getSuggestedAction() {
            return suggestedAction;
        }

        public int getRiskScore() {
            return riskScore;
        }

        public int getObservedCount() {
            return observedCount;
        }
    }

    private static class CidrWhitelistRule {
        private final int network;
        private final int mask;

        private CidrWhitelistRule(int network, int mask) {
            this.network = network;
            this.mask = mask;
        }

        static CidrWhitelistRule parse(String cidr) {
            try {
                String[] parts = cidr.split("/");
                if (parts.length != 2) {
                    return null;
                }
                int prefix = Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > 32) {
                    return null;
                }
                Integer ipInt = ipv4ToInt(parts[0]);
                if (ipInt == null) {
                    return null;
                }
                int mask = prefix == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefix));
                int network = ipInt & mask;
                return new CidrWhitelistRule(network, mask);
            } catch (Exception e) {
                return null;
            }
        }

        boolean matches(String ip) {
            Integer ipInt = ipv4ToInt(ip);
            if (ipInt == null) {
                return false;
            }
            return (ipInt & mask) == network;
        }

        private static Integer ipv4ToInt(String ip) {
            if (ip == null) {
                return null;
            }
            String[] arr = ip.trim().split("\\.");
            if (arr.length != 4) {
                return null;
            }
            int value = 0;
            for (String part : arr) {
                int octet;
                try {
                    octet = Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (octet < 0 || octet > 255) {
                    return null;
                }
                value = (value << 8) | octet;
            }
            return value;
        }
    }
}
