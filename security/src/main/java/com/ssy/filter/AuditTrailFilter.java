package com.ssy.filter;

import com.alibaba.fastjson.JSON;
import com.ssy.context.AuditTraceContext;
import com.ssy.context.RequestUserContext;
import com.ssy.entity.AuditLogRecordEntity;
import com.ssy.holder.AuditTraceContextHolder;
import com.ssy.service.impl.AuditLogAsyncRecorderService;
import com.ssy.service.impl.EndpointRbacCacheService;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AuditTrailFilter extends OncePerRequestFilter {

    /**
     * 安全模块关键词——moduleGroup（来自 @Api tag 或 Controller 类名去掉 "Controller" 后缀）
     * 包含以下任一关键词即归类为 SECURITY，其余均归类为 BUSINESS。
     *
     * 新增业务接口（如 OrderController / ProductController）无需修改此处，
     * 扫描进 api_endpoints 表后自动归为 BUSINESS；
     * 仅在新增全新安全模块类型（且其命名不包含以下关键词）时才需扩充。
     */
    private static final Set<String> SECURITY_MODULE_KEYWORDS = new HashSet<>(Arrays.asList(
            // 英文（来自 Controller 类名去掉 Controller 后缀，如 IamUser, ThreatDetection）
            "iam", "auth", "security", "threat", "audit", "permission", "endpoint",
            // 中文（来自 @Api(tags = ...) 注解）
            "权限", "安全", "审计", "威胁", "认证", "鉴权", "登录", "授权", "封禁", "黑名单", "白名单"
    ));

    private final AuditLogAsyncRecorderService auditLogAsyncRecorderService;
    private final EndpointRbacCacheService endpointRbacCacheService;

    public AuditTrailFilter(AuditLogAsyncRecorderService auditLogAsyncRecorderService,
                            EndpointRbacCacheService endpointRbacCacheService) {
        this.auditLogAsyncRecorderService = auditLogAsyncRecorderService;
        this.endpointRbacCacheService = endpointRbacCacheService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null
                || uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/webjars/")
                || uri.startsWith("/swagger")
                || uri.startsWith("/v2/api-docs")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/favicon");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        AuditTraceContext traceContext = buildTraceContext(requestWrapper, traceId);
        AuditTraceContextHolder.set(traceContext);
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            enrichTraceSamples(traceContext, requestWrapper, responseWrapper);
            recordAudit(requestWrapper, responseWrapper, traceId, System.currentTimeMillis() - start, traceContext);
            responseWrapper.copyBodyToResponse();
            AuditTraceContextHolder.clear();
        }
    }

    private AuditTraceContext buildTraceContext(HttpServletRequest request, String traceId) {
        AuditTraceContext context = new AuditTraceContext();
        context.setTraceId(traceId);
        context.setRequestMethod(request.getMethod());
        context.setRequestUri(request.getRequestURI());
        context.setClientIp(resolveClientIp(request));
        EndpointRbacCacheService.EndpointAccessRule rule = endpointRbacCacheService.match(request.getMethod(), request.getRequestURI());
        if (rule != null) {
            context.setEndpointId(rule.getEndpointId());
            context.setModuleGroup(rule.getModuleGroup());
            context.setDescription(rule.getDescription());
            context.setPermissionCodes(new ArrayList<>(rule.getPermissionCodes()));
        }
        return context;
    }

    private void enrichTraceSamples(AuditTraceContext context,
                                    ContentCachingRequestWrapper request,
                                    ContentCachingResponseWrapper response) {
        if (context == null) {
            return;
        }
        context.setRequestBodySample(extractBodySample(request.getContentAsByteArray(), request.getCharacterEncoding()));
        context.setResponseBodySample(extractBodySample(response.getContentAsByteArray(), response.getCharacterEncoding()));
    }

    private void recordAudit(HttpServletRequest request, HttpServletResponse response, String traceId, long costMs, AuditTraceContext traceContext) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return;
        }

        RequestUserContext context = (RequestUserContext) request.getAttribute(RequestUserContextFilter.REQUEST_USER_CONTEXT_ATTR);
        String method = request.getMethod();
        int responseCode = response.getStatus();
        // 动态分类：优先从 api_endpoints 表的 moduleGroup 判定，回退到路径规则
        String category = resolveCategory(uri, responseCode, traceContext);

        AuditLogRecordEntity entity = new AuditLogRecordEntity();
        entity.setCategory(category);
        entity.setEventType(method);
        entity.setModuleName(traceContext != null && traceContext.getModuleGroup() != null ? traceContext.getModuleGroup() : resolveModule(uri));
        entity.setOperationName(traceContext != null && traceContext.getDescription() != null ? traceContext.getDescription() : buildOperationName(method, uri));
        entity.setResourceType("HTTP_REQUEST");
        entity.setResourceId(method + " " + uri);
        entity.setSuccess(responseCode < 400 ? 1 : 0);
        entity.setDetailText("status=" + responseCode + ", costMs=" + costMs);
        entity.setRequestMethod(method);
        entity.setRequestUri(uri);
        entity.setClientIp(resolveClientIp(request));
        entity.setResponseCode(responseCode);
        entity.setTraceId(traceId);
        entity.setExtJson(JSON.toJSONString(traceContext));
        entity.setCreateTime(LocalDateTime.now());
        // 接口描述——来自 api_endpoints 表的 description 字段
        if (traceContext != null && traceContext.getDescription() != null) {
            entity.setApiDescription(traceContext.getDescription());
        }

        if (context != null) {
            entity.setUsername(context.getUsername());
            entity.setUserId(context.getUserId());
            entity.setLoginType(context.getLoginType());
        } else {
            // 登录请求在认证完成前没有 RequestUserContext，从请求体提取用户名
            resolveLoginUsername(request, entity);
        }
        auditLogAsyncRecorderService.record(entity);
    }

    private String extractBodySample(byte[] bytes, String encoding) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            String charset = encoding == null ? StandardCharsets.UTF_8.name() : encoding;
            String text = new String(bytes, charset);
            return text.length() <= 2000 ? text : text.substring(0, 2000);
        } catch (Exception ignored) {
            try {
                return StreamUtils.copyToString(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return null;
            }
        }
    }

    /**
     * 动态分类策略（自动匹配 api_endpoints 表，新增接口无需改代码）：
     *
     * ① 如果请求命中了 api_endpoints 表中的记录（traceContext.moduleGroup != null），
     *    则用 moduleGroup 关键词匹配判定 SECURITY / BUSINESS；
     * ② 如果未命中（如 /login 由 Spring Security Filter 处理，不属于 Controller 接口），
     *    则回退到最小路径规则 + HTTP 状态码判定。
     *
     * 这样新增业务 Controller → 被扫描进 api_endpoints → moduleGroup 不含安全关键词 → 自动归 BUSINESS。
     */
    private String resolveCategory(String uri, int responseCode, AuditTraceContext traceContext) {
        // ① 优先从 api_endpoints 表缓存动态判定
        if (traceContext != null && traceContext.getModuleGroup() != null) {
            return isSecurityModule(traceContext.getModuleGroup()) ? "SECURITY" : "BUSINESS";
        }
        // ② 未匹配到 api_endpoints 的回退规则
        //    /login、/security-auth 由 Filter 链处理（不经过 Controller），不在 api_endpoints 表中
        if (uri.startsWith("/login") || uri.startsWith("/security-auth")) {
            return "SECURITY";
        }
        // 401/403 响应视为安全事件
        if (responseCode == 401 || responseCode == 403) {
            return "SECURITY";
        }
        return "BUSINESS";
    }

    /**
     * 判断 moduleGroup 是否属于安全模块。
     * moduleGroup 值来自 @Api(tags) 注解或 Controller 类名去掉 "Controller" 后缀。
     */
    private boolean isSecurityModule(String moduleGroup) {
        String lower = moduleGroup.toLowerCase();
        for (String keyword : SECURITY_MODULE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 登录请求特殊处理：从缓存的请求体中提取 username。
     * /login 和 /login-admin 由 Spring Security Filter 处理，在认证完成前没有用户上下文，
     * 但 ContentCachingRequestWrapper 已缓存了请求体，可安全读取。
     */
    private void resolveLoginUsername(HttpServletRequest request, AuditLogRecordEntity entity) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/login")) {
            return;
        }
        if (!(request instanceof ContentCachingRequestWrapper)) {
            return;
        }
        byte[] body = ((ContentCachingRequestWrapper) request).getContentAsByteArray();
        if (body == null || body.length == 0) {
            return;
        }
        try {
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            com.alibaba.fastjson.JSONObject json = JSON.parseObject(bodyStr);
            if (json != null) {
                String username = json.getString("username");
                if (username != null && !username.trim().isEmpty()) {
                    entity.setUsername(username.trim());
                }
            }
        } catch (Exception ignored) {
            // 解析失败不影响审计记录
        }
    }

    private String resolveModule(String uri) {
        if (uri.startsWith("/iam/")) {
            return "IAM";
        }
        if (uri.startsWith("/threat-detection")) {
            return "THREAT_DETECTION";
        }
        if (uri.startsWith("/security-admin")) {
            return "SECURITY_ADMIN";
        }
        if (uri.startsWith("/api/service")) {
            return "SERVICE_PERMISSION";
        }
        if (uri.startsWith("/login")) {
            return "AUTH";
        }
        return "SYSTEM";
    }

    private String buildOperationName(String method, String uri) {
        return method + " " + uri;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            int comma = xff.indexOf(',');
            return (comma >= 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.trim().isEmpty()) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }
}
