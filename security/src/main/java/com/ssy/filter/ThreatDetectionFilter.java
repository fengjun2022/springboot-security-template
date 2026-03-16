package com.ssy.filter;

import com.alibaba.fastjson.JSON;
import com.common.result.Result;
import com.ssy.entity.SecurityAttackEventEntity;
import com.ssy.filter.support.CachedBodyHttpServletRequest;
import com.ssy.filter.support.StatusCaptureHttpServletResponse;
import com.ssy.properties.ThreatDetectionProperties;
import com.ssy.service.impl.AttackEventAsyncRecorderService;
import com.ssy.service.impl.EndpointThreatCacheService;
import com.ssy.service.impl.IpAccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 异常攻击识别过滤器（第一阶段实现）
 * - IP黑名单拦截
 * - 高频访问识别与自动拉黑
 * - 常见SQL注入/XSS/路径穿越/扫描探测识别
 * - 异常事件异步落库
 */
@Order(0)
public class ThreatDetectionFilter extends OncePerRequestFilter {

    private static final Pattern SQLI_PATTERN = Pattern.compile(
            "(?i)(\\bunion\\b\\s+\\bselect\\b|\\bselect\\b.+\\bfrom\\b|\\bdrop\\b\\s+\\btable\\b|\\binsert\\b\\s+\\binto\\b|\\bor\\b\\s+1=1|--|/\\*|\\bbenchmark\\b\\(|\\bsleep\\b\\()"
    );
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<\\s*script|javascript:|onerror\\s*=|onload\\s*=|<\\s*img[^>]+onerror)"
    );
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(?i)(\\.\\./|\\.\\.\\\\|%2e%2e%2f|%2e%2e/|%252e%252e)"
    );

    private static final Set<String> LIGHT_SKIP_PREFIXES = new HashSet<>(Arrays.asList(
            "/css/", "/js/", "/webjars/", "/swagger-ui", "/swagger-resources",
            "/v2/api-docs", "/v3/api-docs", "/favicon.ico", "/doc.html"
    ));
    private static final Set<String> AUTH_FEEDBACK_SKIP_PATHS = new HashSet<>(Arrays.asList(
            "/login", "/login-admin", "/error"
    ));

    @Autowired
    private ThreatDetectionProperties properties;

    @Autowired
    private EndpointThreatCacheService endpointThreatCacheService;

    @Autowired
    private IpAccessControlService ipAccessControlService;

    @Autowired
    private AttackEventAsyncRecorderService attackEventAsyncRecorderService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (properties == null || !properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        String method = request.getMethod();
        String path = normalizePath(request.getRequestURI());

        // 黑名单优先：被拉黑IP禁止访问任何接口
        if (ipAccessControlService.isBlacklisted(ip)) {
            BlockDecision decision = BlockDecision.block(
                    "BLACKLIST_HIT",
                    "IP命中黑名单",
                    "检查该IP历史攻击事件并人工确认是否解封",
                    HttpServletResponse.SC_FORBIDDEN,
                    true,
                    95
            );
            recordEventAsync(request, ip, method, path, null, null, decision);
            writeBlockResponse(response, decision.getHttpStatus(), "请求已被安全策略拦截");
            return;
        }

        // 白名单IP：跳过检测（但黑名单优先于白名单）
        if (ipAccessControlService.isWhitelisted(ip)) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        EndpointThreatCacheService.EndpointThreatRule endpointRule = endpointThreatCacheService.match(method, path);
        boolean monitorEnabled = endpointRule == null
                ? properties.isMonitorUnknownEndpoints()
                : endpointRule.getThreatMonitorEnabled() != 0;

        if (!monitorEnabled || shouldLightSkip(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest requestToUse = request;
        String bodySample = null;
        if (shouldCacheBody(request)) {
            try {
                CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
                requestToUse = cachedRequest;
                bodySample = trim(cachedRequest.getCachedBodyAsString(properties.getMaxInspectBodyBytes()), properties.getMaxInspectBodyBytes());
            } catch (Exception e) {
                // 请求体缓存失败不影响主流程
            }
        }

        String endpointKey = (endpointRule == null ? method.toUpperCase(Locale.ROOT) + ":" + path
                : endpointRule.getMethod() + ":" + endpointRule.getPath());

        IpAccessControlService.RateCheckResult rateCheckResult =
                ipAccessControlService.checkRate(ip, endpointKey, true);

        if (!rateCheckResult.isAllow()) {
            BlockDecision decision = BlockDecision.block(
                    rateCheckResult.getAttackType(),
                    rateCheckResult.getReason() + "，计数=" + rateCheckResult.getObservedCount(),
                    rateCheckResult.isShouldBlacklist() ? "已自动临时拉黑该IP，建议核查来源与请求模式" : "建议观察该IP后续行为，必要时加入永久黑名单",
                    429,
                    rateCheckResult.isShouldBlacklist(),
                    rateCheckResult.isShouldBlacklist() ? 90 : 70
            );
            if (decision.isAutoBlacklist()) {
                ipAccessControlService.addToBlacklist(ip, decision.getAttackType(), decision.getReason(), properties.getAutoBlockSeconds());
            }
            recordEventAsync(requestToUse, ip, method, path, endpointRule, bodySample, decision);
            writeBlockResponse(response, decision.getHttpStatus(), "访问频率异常，已触发安全策略");
            return;
        }

        BlockDecision signatureDecision = detectAttack(requestToUse, ip, method, path, bodySample);
        if (signatureDecision != null) {
            if (signatureDecision.isAutoBlacklist()) {
                ipAccessControlService.addToBlacklist(ip, signatureDecision.getAttackType(),
                        signatureDecision.getReason(), properties.getAutoBlockSeconds());
            }
            recordEventAsync(requestToUse, ip, method, path, endpointRule, bodySample, signatureDecision);
            writeBlockResponse(response, signatureDecision.getHttpStatus(), "请求包含可疑攻击特征，已被拦截");
            return;
        }

        StatusCaptureHttpServletResponse statusCaptureResponse = new StatusCaptureHttpServletResponse(response);
        filterChain.doFilter(requestToUse, statusCaptureResponse);

        handleAuthResultFeedback(requestToUse, ip, method, path, endpointRule, bodySample, statusCaptureResponse.getStatus());
    }

    private BlockDecision detectAttack(HttpServletRequest request,
                                       String ip,
                                       String method,
                                       String path,
                                       String bodySample) {
        String queryString = request.getQueryString();
        String lowerPath = path.toLowerCase(Locale.ROOT);

        if (PATH_TRAVERSAL_PATTERN.matcher(path).find() || containsEncodedTraversal(queryString)) {
            return BlockDecision.block("PATH_TRAVERSAL",
                    "检测到路径穿越特征",
                    "建议审计来源IP并排查是否存在目录遍历扫描",
                    HttpServletResponse.SC_FORBIDDEN,
                    true,
                    95);
        }

        if (isScannerPath(lowerPath)) {
            return BlockDecision.block("SCANNER_PROBE",
                    "检测到疑似扫描/探测路径",
                    "建议检查该IP近期请求轨迹，必要时加入永久黑名单",
                    HttpServletResponse.SC_FORBIDDEN,
                    false,
                    65);
        }

        String queryToCheck = queryString == null ? "" : queryString;
        if (SQLI_PATTERN.matcher(queryToCheck).find() || SQLI_PATTERN.matcher(path).find()
                || (bodySample != null && SQLI_PATTERN.matcher(bodySample).find())) {
            return BlockDecision.block("SQL_INJECTION",
                    "检测到SQL注入攻击特征",
                    "建议核查参数过滤规则与日志样本，评估是否需要永久封禁该IP",
                    HttpServletResponse.SC_FORBIDDEN,
                    true,
                    92);
        }

        if (XSS_PATTERN.matcher(queryToCheck).find() || XSS_PATTERN.matcher(path).find()
                || (bodySample != null && XSS_PATTERN.matcher(bodySample).find())) {
            return BlockDecision.block("XSS_ATTACK",
                    "检测到XSS攻击特征",
                    "建议核查输入校验与输出编码策略",
                    HttpServletResponse.SC_FORBIDDEN,
                    false,
                    88);
        }

        // 粗粒度越权探测：高频访问明显管理端路径且未携带认证头（后续可结合401/403埋点增强）
        if (lowerPath.startsWith("/admin") && !StringUtils.hasText(request.getHeader("Authorization"))) {
            if ("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                return BlockDecision.block("PRIVILEGE_PROBE",
                        "疑似未授权访问管理接口",
                        "建议检查访问日志并确认是否存在越权探测",
                        HttpServletResponse.SC_FORBIDDEN,
                        false,
                        60);
            }
        }

        return null;
    }

    private void recordEventAsync(HttpServletRequest request,
                                  String ip,
                                  String method,
                                  String path,
                                  EndpointThreatCacheService.EndpointThreatRule endpointRule,
                                  String bodySample,
                                  BlockDecision decision) {
        SecurityAttackEventEntity event = new SecurityAttackEventEntity();
        event.setIp(ip);
        event.setAttackType(decision.getAttackType());
        event.setPath(path);
        event.setMethod(method);
        event.setEndpointId(endpointRule == null ? null : endpointRule.getEndpointId());
        event.setUsername(resolveUsername());
        event.setAppId(request.getHeader("appid"));
        event.setUserAgent(trim(request.getHeader("User-Agent"), 1000));
        event.setReferer(trim(request.getHeader("Referer"), 1000));
        event.setQueryString(trim(request.getQueryString(), 2000));
        event.setRequestBodySample(trim(bodySample, 4000));
        event.setRequestBodyHash(hashHex(bodySample));
        event.setRiskScore(decision.getRiskScore());
        event.setBlockAction(decision.isAutoBlacklist() ? "BLACKLIST" : "BLOCK");
        event.setBlockReason(decision.getReason());
        event.setSuggestedAction(decision.getSuggestedAction());
        event.setCreateTime(LocalDateTime.now());
        attackEventAsyncRecorderService.record(event);
    }

    private void recordEventAsync(HttpServletRequest request,
                                  String ip,
                                  String method,
                                  String path,
                                  EndpointThreatCacheService.EndpointThreatRule endpointRule,
                                  String bodySample,
                                  String attackType,
                                  String reason,
                                  String suggestedAction,
                                  boolean autoBlacklist,
                                  int riskScore) {
        SecurityAttackEventEntity event = new SecurityAttackEventEntity();
        event.setIp(ip);
        event.setAttackType(attackType);
        event.setPath(path);
        event.setMethod(method);
        event.setEndpointId(endpointRule == null ? null : endpointRule.getEndpointId());
        event.setUsername(resolveUsername());
        event.setAppId(request.getHeader("appid"));
        event.setUserAgent(trim(request.getHeader("User-Agent"), 1000));
        event.setReferer(trim(request.getHeader("Referer"), 1000));
        event.setQueryString(trim(request.getQueryString(), 2000));
        event.setRequestBodySample(trim(bodySample, 4000));
        event.setRequestBodyHash(hashHex(bodySample));
        event.setRiskScore(riskScore);
        event.setBlockAction(autoBlacklist ? "BLACKLIST" : "LOG_ONLY");
        event.setBlockReason(reason);
        event.setSuggestedAction(suggestedAction);
        event.setCreateTime(LocalDateTime.now());
        attackEventAsyncRecorderService.record(event);
    }

    private void handleAuthResultFeedback(HttpServletRequest request,
                                          String ip,
                                          String method,
                                          String path,
                                          EndpointThreatCacheService.EndpointThreatRule endpointRule,
                                          String bodySample,
                                          int statusCode) {
        if (statusCode != HttpServletResponse.SC_UNAUTHORIZED && statusCode != HttpServletResponse.SC_FORBIDDEN) {
            return;
        }

        if (AUTH_FEEDBACK_SKIP_PATHS.contains(path)) {
            return;
        }

        if (ipAccessControlService.isWhitelisted(ip)) {
            return;
        }

        String endpointKey = (endpointRule == null ? method.toUpperCase(Locale.ROOT) + ":" + path
                : endpointRule.getMethod() + ":" + endpointRule.getPath());

        IpAccessControlService.AuthFeedbackResult authFeedbackResult =
                ipAccessControlService.recordAuthResultAndCheck(ip, endpointKey, statusCode);

        if (!authFeedbackResult.isShouldRecord()) {
            return;
        }

        if (authFeedbackResult.isShouldBlacklist()) {
            ipAccessControlService.addToBlacklist(ip, authFeedbackResult.getAttackType(),
                    authFeedbackResult.getReason(), properties.getAutoBlockSeconds());
        }

        recordEventAsync(request, ip, method, path, endpointRule, bodySample,
                authFeedbackResult.getAttackType(),
                authFeedbackResult.getReason(),
                authFeedbackResult.getSuggestedAction(),
                authFeedbackResult.isShouldBlacklist(),
                authFeedbackResult.getRiskScore());
    }

    private String resolveUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception ignored) {
            // no-op
        }
        return null;
    }

    private boolean shouldCacheBody(HttpServletRequest request) {
        if (!properties.isCaptureBodySample()) {
            return false;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return false;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength <= 0 || contentLength > properties.getMaxInspectBodyBytes()) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        contentType = contentType.toLowerCase(Locale.ROOT);
        return contentType.contains("application/json")
                || contentType.contains("application/x-www-form-urlencoded")
                || contentType.contains("text/plain")
                || contentType.contains("application/xml")
                || contentType.contains("text/xml");
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustForwardHeaders()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                int comma = xff.indexOf(',');
                String ip = (comma > 0 ? xff.substring(0, comma) : xff).trim();
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private boolean shouldLightSkip(String path) {
        for (String prefix : LIGHT_SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEncodedTraversal(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return false;
        }
        return PATH_TRAVERSAL_PATTERN.matcher(queryString).find();
    }

    private boolean isScannerPath(String lowerPath) {
        for (String keyword : properties.getScannerPathKeywords()) {
            if (keyword != null && !keyword.isEmpty() && lowerPath.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        normalized = normalized.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String trim(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0 || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private String hashHex(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void writeBlockResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json;charset=UTF-8");
        Result<Object> result = Result.error(message);
        try (PrintWriter writer = response.getWriter()) {
            writer.write(JSON.toJSONString(result));
            writer.flush();
        }
    }

    private static class BlockDecision {
        private final String attackType;
        private final String reason;
        private final String suggestedAction;
        private final int httpStatus;
        private final boolean autoBlacklist;
        private final int riskScore;

        private BlockDecision(String attackType, String reason, String suggestedAction, int httpStatus, boolean autoBlacklist, int riskScore) {
            this.attackType = attackType;
            this.reason = reason;
            this.suggestedAction = suggestedAction;
            this.httpStatus = httpStatus;
            this.autoBlacklist = autoBlacklist;
            this.riskScore = riskScore;
        }

        static BlockDecision block(String attackType, String reason, String suggestedAction, int httpStatus, boolean autoBlacklist, int riskScore) {
            return new BlockDecision(attackType, reason, suggestedAction, httpStatus, autoBlacklist, riskScore);
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

        public int getHttpStatus() {
            return httpStatus;
        }

        public boolean isAutoBlacklist() {
            return autoBlacklist;
        }

        public int getRiskScore() {
            return riskScore;
        }
    }
}
