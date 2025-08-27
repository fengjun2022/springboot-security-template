package com.ssy.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * Token自动头部过滤器
 * 系统层面自动处理token的获取和设置
 *
 * 工作原理：
 * 1. 检查请求是否已有Authorization头
 * 2. 如果没有，从Cookie中获取admin_token
 * 3. 如果Cookie中也没有，从请求参数中获取token
 * 4. 将获取到的token设置到Authorization头中
 */
@Slf4j
@Component
public class TokenAutoHeaderFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_TOKEN_COOKIE = "admin_token";
    private static final String TOKEN_PARAM = "token";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        log.debug("🔍 TokenAutoHeaderFilter处理请求: {}", requestURI);

        // 检查是否是需要处理的请求路径
        if (shouldProcessRequest(requestURI)) {
            // 检查是否已有Authorization头
            String existingAuth = request.getHeader(AUTHORIZATION_HEADER);

            if (existingAuth == null || existingAuth.trim().isEmpty()) {
                log.debug("📝 请求缺少Authorization头，尝试自动获取token");

                // 尝试从多种来源获取token
                String token = getTokenFromSources(request);

                if (token != null && !token.trim().isEmpty()) {
                    log.debug("✅ 找到token，正在设置到请求头: {}", token.substring(0, Math.min(50, token.length())) + "...");

                    // 创建包装的请求，添加Authorization头
                    HttpServletRequest wrappedRequest = new TokenRequestWrapper(request, token);
                    log.debug("🔄 转发到下一个过滤器，Authorization头: {}", wrappedRequest.getHeader(AUTHORIZATION_HEADER));
                    filterChain.doFilter(wrappedRequest, response);
                    return;
                } else {
                    log.debug("❌ 未找到有效的token");
                }
            } else {
                log.debug("✅ 请求已有Authorization头: {}",
                        existingAuth.substring(0, Math.min(20, existingAuth.length())) + "...");
            }
        }

        // 继续正常的请求处理
        filterChain.doFilter(request, response);
    }

    /**
     * 判断是否需要处理该请求
     */
    private boolean shouldProcessRequest(String requestURI) {
        // 处理admin相关的请求
        if (requestURI.startsWith("/admin/")) {
            return true;
        }

        // 处理API请求
        if (requestURI.startsWith("/api/")) {
            return true;
        }

        return false;
    }

    /**
     * 从多种来源获取token
     * 优先级：Cookie > 请求参数 > 其他
     */
    private String getTokenFromSources(HttpServletRequest request) {
        String token = null;

        // 1. 尝试从Cookie获取
        token = getTokenFromCookie(request);
        if (token != null) {
            log.debug("🍪 从Cookie中获取到token");
            return ensureBearerPrefix(token);
        }

        // 2. 尝试从请求参数获取
        token = getTokenFromParameter(request);
        if (token != null) {
            log.debug("📋 从请求参数中获取到token");
            return ensureBearerPrefix(token);
        }

        // 3. 尝试从请求头获取（其他可能的头部）
        token = getTokenFromOtherHeaders(request);
        if (token != null) {
            log.debug("📨 从其他头部获取到token");
            return ensureBearerPrefix(token);
        }

        return null;
    }

    /**
     * 从Cookie中获取token
     */
    private String getTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ADMIN_TOKEN_COOKIE.equals(cookie.getName())) {
                    String token = cookie.getValue();
                    log.debug("🍪 从Cookie '{}' 获取到token: {}", ADMIN_TOKEN_COOKIE, token != null ? token.substring(0, Math.min(30, token.length())) + "..." : "null");
                    if (token != null && !token.trim().isEmpty()) {
                        return token;
                    }
                }
            }
        } else {
            log.debug("🍪 请求中没有Cookie");
        }
        return null;
    }

    /**
     * 从请求参数中获取token
     */
    private String getTokenFromParameter(HttpServletRequest request) {
        String token = request.getParameter(TOKEN_PARAM);
        if (token != null && !token.trim().isEmpty()) {
            return token;
        }

        // 尝试其他可能的参数名
        token = request.getParameter("auth_token");
        if (token != null && !token.trim().isEmpty()) {
            return token;
        }

        return null;
    }

    /**
     * 从其他可能的请求头获取token
     */
    private String getTokenFromOtherHeaders(HttpServletRequest request) {
        // 尝试从X-Auth-Token头获取
        String token = request.getHeader("X-Auth-Token");
        if (token != null && !token.trim().isEmpty()) {
            return token;
        }

        // 尝试从X-Token头获取
        token = request.getHeader("X-Token");
        if (token != null && !token.trim().isEmpty()) {
            return token;
        }

        return null;
    }

    /**
     * 确保token有正确的Bearer前缀
     */
    private String ensureBearerPrefix(String token) {
        if (token == null) {
            return null;
        }

        token = token.trim();

        // 如果已经包含Bearer前缀，直接返回
        if (token.startsWith(BEARER_PREFIX)) {
            return token;
        }

        // 如果是JWT格式（以ey开头），添加Bearer前缀
        if (token.startsWith("ey")) {
            return BEARER_PREFIX + token;
        }

        // 其他情况直接返回
        return token;
    }

    /**
     * 请求包装器，用于添加Authorization头
     */
    private static class TokenRequestWrapper extends HttpServletRequestWrapper {

        private final String token;
        private final Map<String, String> additionalHeaders;

        public TokenRequestWrapper(HttpServletRequest request, String token) {
            super(request);
            this.token = token;
            this.additionalHeaders = new HashMap<>();
            this.additionalHeaders.put(AUTHORIZATION_HEADER, token);
        }

        @Override
        public String getHeader(String name) {
            if (additionalHeaders.containsKey(name)) {
                return additionalHeaders.get(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (additionalHeaders.containsKey(name)) {
                return Collections.enumeration(Arrays.asList(additionalHeaders.get(name)));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> headerNames = new HashSet<>(additionalHeaders.keySet());
            Enumeration<String> originalHeaders = super.getHeaderNames();
            while (originalHeaders.hasMoreElements()) {
                headerNames.add(originalHeaders.nextElement());
            }
            return Collections.enumeration(headerNames);
        }
    }
}
