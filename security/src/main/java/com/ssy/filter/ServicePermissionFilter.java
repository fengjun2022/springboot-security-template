package com.ssy.filter;

import com.alibaba.fastjson.JSON;
import com.common.result.Result;
import com.ssy.entity.ServiceTokenEntity;
import com.ssy.service.PermissionCacheService;
import com.ssy.service.ServiceTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 服务权限验证Filter
 * 验证服务间调用的权限，基于appId和token进行验证
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */

@Order(1) // 设置高优先级，早于其他Filter执行
public class ServicePermissionFilter extends OncePerRequestFilter {

    @Autowired
    private PermissionCacheService permissionCacheService;

    @Autowired
    private ServiceTokenService serviceTokenService;

    /**
     * 排除的URL路径，这些路径不进行服务权限验证
     */
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
            "/api/service-app/",
            "/api/service-token/",
            "/swagger-ui",
            "/swagger-resources",
            "/v2/api-docs",
            "/webjars/",
            "/favicon.ico",
            "/doc.html",
            "/error"));

    /**
     * 服务调用标识Header
     */
    private static final String SERVICE_CALL_HEADER = "X-Service-Call";
    private static final String APP_ID_HEADER = "appid";
    private static final String TOKEN_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // 检查是否需要跳过验证
        if (shouldSkip(request, requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 检查是否为服务间调用
        String serviceCallFlag = request.getHeader(SERVICE_CALL_HEADER);

        if (!"true".equals(serviceCallFlag)) {
            filterChain.doFilter(request, response);
            return;
        }


        // 获取appId
        String appId = request.getHeader(APP_ID_HEADER);

        if (!StringUtils.hasText(appId)) {
            writeErrorResponse(response, "缺少appId参数");
            return;
        }

        // 获取token
        String authorization = request.getHeader(TOKEN_HEADER);

        if (!StringUtils.hasText(authorization) || !authorization.startsWith(TOKEN_PREFIX)) {
            writeErrorResponse(response, "缺少有效的Authorization Token");
            return;
        }

        String token = authorization.substring(TOKEN_PREFIX.length());

        // 验证token
        ServiceTokenEntity serviceToken = serviceTokenService.validateToken(token);

        if (serviceToken == null) {
            writeErrorResponse(response, "Token验证失败");
            return;
        }

        // 检查token对应的appId是否一致
        if (!appId.equals(serviceToken.getAppId())) {
            writeErrorResponse(response, "Token与appId不匹配");
            return;
        }

        // 检查权限
        boolean hasPermission = permissionCacheService.hasPermission(appId, requestPath);

        if (!hasPermission) {
            System.err.println("=== 无权限访问该接口: "+ "appId"+appId + requestPath);
            writeErrorResponse(response, "无权限访问该接口: " + requestPath);
            return;
        }

        request.setAttribute("SERVICE_CALL_VERIFIED", true);


        // *** 关键修复：设置服务认证上下文 ***
        // 创建一个服务认证主体，告诉Spring Security这是一个已认证的服务请求
        UsernamePasswordAuthenticationToken serviceAuth = new UsernamePasswordAuthenticationToken(
                "SERVICE_" + appId, // 主体标识
                null, // 凭证
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SERVICE")) // 服务角色
        );
        SecurityContextHolder.getContext().setAuthentication(serviceAuth);

        // 权限验证通过，继续执行
        filterChain.doFilter(request, response);
    }

    /**
     * 判断是否应该跳过权限验证
     */
    private boolean shouldSkip(HttpServletRequest request, String requestPath) {
        // 检查排除路径
        for (String excludedPath : EXCLUDED_PATHS) {
            if (requestPath.startsWith(excludedPath)) {
                return true;
            }
        }

        // OPTIONS请求跳过
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        return false;
    }

    /**
     * 写入错误响应
     */
    private void writeErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        Result<Object> result = Result.error(message);
        String jsonResponse = JSON.toJSONString(result);

        PrintWriter writer = response.getWriter();
        writer.write(jsonResponse);
        writer.flush();
        writer.close();
    }
}