package com.ssy.service.impl;

import com.ssy.entity.ApiEndpointEntity;
import com.ssy.mapper.ApiEndpointMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * API接口异常识别配置缓存。
 * 启动后将 api_endpoints 装载进内存，热路径仅做内存匹配。
 */
@Service
public class EndpointThreatCacheService {

    private static final Logger log = LoggerFactory.getLogger(EndpointThreatCacheService.class);

    private final ApiEndpointMapper apiEndpointMapper;

    private final AtomicReference<Snapshot> snapshotRef = new AtomicReference<>(Snapshot.empty());

    public EndpointThreatCacheService(ApiEndpointMapper apiEndpointMapper) {
        this.apiEndpointMapper = apiEndpointMapper;
    }

    public void refresh() {
        long start = System.currentTimeMillis();

        List<ApiEndpointEntity> endpoints = apiEndpointMapper.selectAll();
        Map<String, EndpointThreatRule> exactRules = new ConcurrentHashMap<>();
        Map<String, List<PatternRule>> patternRulesByMethod = new ConcurrentHashMap<>();

        int exactCount = 0;
        int patternCount = 0;
        for (ApiEndpointEntity endpoint : endpoints) {
            if (endpoint.getPath() == null || endpoint.getMethod() == null) {
                continue;
            }

            EndpointThreatRule rule = new EndpointThreatRule(
                    endpoint.getId(),
                    normalizePath(endpoint.getPath()),
                    endpoint.getMethod().toUpperCase(),
                    endpoint.getStatus() == null ? 1 : endpoint.getStatus(),
                    endpoint.getThreatMonitorEnabled() == null ? 1 : endpoint.getThreatMonitorEnabled()
            );

            if (isPatternPath(rule.getPath())) {
                String method = rule.getMethod();
                patternRulesByMethod.computeIfAbsent(method, k -> new ArrayList<>())
                        .add(new PatternRule(compilePathPattern(rule.getPath()), rule));
                patternCount++;
            } else {
                exactRules.put(buildKey(rule.getMethod(), rule.getPath()), rule);
                exactCount++;
            }
        }

        // 收敛为不可变快照，避免运行期被修改
        Snapshot snapshot = new Snapshot(exactRules, freezePatternMap(patternRulesByMethod));
        snapshotRef.set(snapshot);

        log.info("异常识别接口缓存已刷新: total={}, exact={}, pattern={}, cost={}ms",
                endpoints.size(), exactCount, patternCount, (System.currentTimeMillis() - start));
    }

    public EndpointThreatRule match(String method, String requestPath) {
        Snapshot snapshot = snapshotRef.get();
        String normalizedPath = normalizePath(requestPath);
        String normalizedMethod = method == null ? "GET" : method.toUpperCase();

        EndpointThreatRule exact = snapshot.exactRules.get(buildKey(normalizedMethod, normalizedPath));
        if (exact != null) {
            return exact;
        }

        List<PatternRule> patternRules = snapshot.patternRulesByMethod.get(normalizedMethod);
        if (patternRules == null || patternRules.isEmpty()) {
            return null;
        }
        for (PatternRule patternRule : patternRules) {
            if (patternRule.pattern.matcher(normalizedPath).matches()) {
                return patternRule.rule;
            }
        }
        return null;
    }

    public int size() {
        Snapshot snapshot = snapshotRef.get();
        int patternSize = snapshot.patternRulesByMethod.values().stream().mapToInt(List::size).sum();
        return snapshot.exactRules.size() + patternSize;
    }

    private Map<String, List<PatternRule>> freezePatternMap(Map<String, List<PatternRule>> source) {
        Map<String, List<PatternRule>> result = new ConcurrentHashMap<>();
        source.forEach((k, v) -> result.put(k, Collections.unmodifiableList(new ArrayList<>(v))));
        return result;
    }

    private String buildKey(String method, String path) {
        return method + '\n' + path;
    }

    private boolean isPatternPath(String path) {
        return path.indexOf('{') >= 0 || path.indexOf('*') >= 0;
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

    private Pattern compilePathPattern(String path) {
        String normalized = normalizePath(path);
        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '{') {
                int end = normalized.indexOf('}', i);
                if (end > i) {
                    regex.append("[^/]+");
                    i = end;
                    continue;
                }
            }
            if (c == '*') {
                boolean isDoubleStar = (i + 1 < normalized.length() && normalized.charAt(i + 1) == '*');
                regex.append(isDoubleStar ? ".*" : "[^/]*");
                if (isDoubleStar) {
                    i++;
                }
                continue;
            }

            if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\');
            }
            regex.append(c);
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    public static class EndpointThreatRule {
        private final Long endpointId;
        private final String path;
        private final String method;
        private final int status;
        private final int threatMonitorEnabled;

        public EndpointThreatRule(Long endpointId, String path, String method, int status, int threatMonitorEnabled) {
            this.endpointId = endpointId;
            this.path = path;
            this.method = method;
            this.status = status;
            this.threatMonitorEnabled = threatMonitorEnabled;
        }

        public Long getEndpointId() {
            return endpointId;
        }

        public String getPath() {
            return path;
        }

        public String getMethod() {
            return method;
        }

        public int getStatus() {
            return status;
        }

        public int getThreatMonitorEnabled() {
            return threatMonitorEnabled;
        }
    }

    private static class PatternRule {
        private final Pattern pattern;
        private final EndpointThreatRule rule;

        private PatternRule(Pattern pattern, EndpointThreatRule rule) {
            this.pattern = pattern;
            this.rule = rule;
        }
    }

    private static class Snapshot {
        private final Map<String, EndpointThreatRule> exactRules;
        private final Map<String, List<PatternRule>> patternRulesByMethod;

        private Snapshot(Map<String, EndpointThreatRule> exactRules,
                         Map<String, List<PatternRule>> patternRulesByMethod) {
            this.exactRules = exactRules;
            this.patternRulesByMethod = patternRulesByMethod;
        }

        private static Snapshot empty() {
            return new Snapshot(Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
