package com.ssy.service.impl;

import com.ssy.entity.ApiEndpointEntity;
import com.ssy.mapper.ApiEndpointMapper;
import com.ssy.mapper.RbacPermissionEndpointRelMapper;
import com.ssy.mapper.RbacPermissionEndpointRelMapper.EndpointPermissionCodeRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 接口级RBAC缓存（基于 api_endpoints + sys_permission_endpoint_rel）
 * 热路径只做：
 * 1) 路由命中
 * 2) permission Set contains 判断
 */
@Service
public class EndpointRbacCacheService {

    private static final Logger log = LoggerFactory.getLogger(EndpointRbacCacheService.class);

    private final ApiEndpointMapper apiEndpointMapper;
    private final RbacPermissionEndpointRelMapper endpointRelMapper;
    private final AtomicReference<Snapshot> snapshotRef = new AtomicReference<>(Snapshot.empty());

    public EndpointRbacCacheService(ApiEndpointMapper apiEndpointMapper,
                                    RbacPermissionEndpointRelMapper endpointRelMapper) {
        this.apiEndpointMapper = apiEndpointMapper;
        this.endpointRelMapper = endpointRelMapper;
    }

    public void refresh() {
        long start = System.currentTimeMillis();

        List<ApiEndpointEntity> endpoints = apiEndpointMapper.selectAll();
        List<EndpointPermissionCodeRow> bindings = endpointRelMapper.selectAllActiveEndpointPermissionCodes();

        Map<Long, LinkedHashSet<String>> permissionMap = new HashMap<>();
        for (EndpointPermissionCodeRow row : bindings) {
            if (row == null || row.getEndpointId() == null || !StringUtils.hasText(row.getPermCode())) {
                continue;
            }
            permissionMap.computeIfAbsent(row.getEndpointId(), k -> new LinkedHashSet<>()).add(row.getPermCode().trim());
        }

        Map<String, EndpointAccessRule> exactRules = new ConcurrentHashMap<>();
        Map<String, List<PatternRule>> patternRulesByMethod = new ConcurrentHashMap<>();

        int exact = 0;
        int pattern = 0;
        int protectedCount = 0;
        int permissionBoundCount = 0;
        for (ApiEndpointEntity endpoint : endpoints) {
            if (!StringUtils.hasText(endpoint.getPath()) || !StringUtils.hasText(endpoint.getMethod())) {
                continue;
            }
            List<String> perms = permissionMap.containsKey(endpoint.getId())
                    ? Collections.unmodifiableList(new ArrayList<>(permissionMap.get(endpoint.getId())))
                    : Collections.emptyList();

            EndpointAccessRule rule = buildRule(endpoint, perms);
            if (rule.isRequireAuth()) {
                protectedCount++;
            }
            if (rule.hasPermissionBindings()) {
                permissionBoundCount++;
            }

            if (isPatternPath(rule.getPath())) {
                patternRulesByMethod.computeIfAbsent(rule.getMethod(), k -> new ArrayList<>())
                        .add(new PatternRule(compilePathPattern(rule.getPath()), rule));
                pattern++;
            } else {
                exactRules.put(buildKey(rule.getMethod(), rule.getPath()), rule);
                exact++;
            }
        }

        snapshotRef.set(new Snapshot(exactRules, freezePatternMap(patternRulesByMethod)));
        log.info("接口RBAC缓存已刷新: total={}, protected={}, permissionBound={}, exact={}, pattern={}, cost={}ms",
                endpoints.size(), protectedCount, permissionBoundCount, exact, pattern, (System.currentTimeMillis() - start));
    }

    public EndpointAccessRule match(String method, String requestPath) {
        Snapshot snapshot = snapshotRef.get();
        String normalizedMethod = normalizeMethod(method);
        String normalizedPath = normalizePath(requestPath);

        EndpointAccessRule exact = snapshot.exactRules.get(buildKey(normalizedMethod, normalizedPath));
        if (exact != null) {
            return exact;
        }
        List<PatternRule> rules = snapshot.patternRulesByMethod.get(normalizedMethod);
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (PatternRule rule : rules) {
            if (rule.pattern.matcher(normalizedPath).matches()) {
                return rule.rule;
            }
        }
        return null;
    }

    public int size() {
        Snapshot snapshot = snapshotRef.get();
        int patternSize = snapshot.patternRulesByMethod.values().stream().mapToInt(List::size).sum();
        return snapshot.exactRules.size() + patternSize;
    }

    private EndpointAccessRule buildRule(ApiEndpointEntity endpoint, List<String> permissionCodes) {
        int status = endpoint.getStatus() == null ? 1 : endpoint.getStatus();
        boolean requireAuth = endpoint.getRequireAuth() != null && endpoint.getRequireAuth() == 1;
        if (!permissionCodes.isEmpty()) {
            requireAuth = true;
        }

        return new EndpointAccessRule(
                endpoint.getId(),
                normalizePath(endpoint.getPath()),
                normalizeMethod(endpoint.getMethod()),
                status,
                requireAuth,
                permissionCodes
        );
    }

    private Map<String, List<PatternRule>> freezePatternMap(Map<String, List<PatternRule>> source) {
        Map<String, List<PatternRule>> result = new ConcurrentHashMap<>();
        source.forEach((k, v) -> result.put(k, Collections.unmodifiableList(new ArrayList<>(v))));
        return result;
    }

    private String buildKey(String method, String path) {
        return method + '\n' + path;
    }

    private String normalizeMethod(String method) {
        return method == null ? "GET" : method.trim().toUpperCase(Locale.ROOT);
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

    private boolean isPatternPath(String path) {
        return path.indexOf('{') >= 0 || path.indexOf('*') >= 0;
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

    public static class EndpointAccessRule {
        private final Long endpointId;
        private final String path;
        private final String method;
        private final int status;
        private final boolean requireAuth;
        private final List<String> permissionCodes;

        public EndpointAccessRule(Long endpointId, String path, String method, int status,
                                  boolean requireAuth, List<String> permissionCodes) {
            this.endpointId = endpointId;
            this.path = path;
            this.method = method;
            this.status = status;
            this.requireAuth = requireAuth;
            this.permissionCodes = permissionCodes == null ? Collections.emptyList() : permissionCodes;
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

        public boolean isRequireAuth() {
            return requireAuth;
        }

        public List<String> getPermissionCodes() {
            return permissionCodes;
        }

        public boolean hasPermissionBindings() {
            return !permissionCodes.isEmpty();
        }
    }

    private static class PatternRule {
        private final Pattern pattern;
        private final EndpointAccessRule rule;

        private PatternRule(Pattern pattern, EndpointAccessRule rule) {
            this.pattern = pattern;
            this.rule = rule;
        }
    }

    private static class Snapshot {
        private final Map<String, EndpointAccessRule> exactRules;
        private final Map<String, List<PatternRule>> patternRulesByMethod;

        private Snapshot(Map<String, EndpointAccessRule> exactRules,
                         Map<String, List<PatternRule>> patternRulesByMethod) {
            this.exactRules = exactRules;
            this.patternRulesByMethod = patternRulesByMethod;
        }

        private static Snapshot empty() {
            return new Snapshot(Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
