package com.ssy.filter;

import com.alibaba.fastjson.JSON;
import com.ssy.context.RequestUserContext;
import com.ssy.entity.HttpMessage;
import com.ssy.entity.HttpStatus;
import com.ssy.entity.Result;
import com.ssy.holder.RequestUserContextHolder;
import com.ssy.properties.SecurityProperties;
import com.ssy.service.impl.EndpointRbacCacheService;
import com.ssy.service.impl.EndpointRbacCacheService.EndpointAccessRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 基于 api_endpoints + sys_permission_endpoint_rel 的接口级 RBAC 鉴权前置过滤器。
 * 热路径只做内存匹配与 permission Set contains 判断。
 */
public class EndpointRbacAuthorizationFilter extends OncePerRequestFilter {

    private static final String SERVICE_CALL_HEADER = "X-Service-Call";

    @Autowired
    private EndpointRbacCacheService endpointRbacCacheService;
    @Autowired(required = false)
    private SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // OPTIONS 放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 服务间调用走 ServicePermissionFilter，不走用户RBAC接口级校验
        if ("true".equalsIgnoreCase(request.getHeader(SERVICE_CALL_HEADER))) {
            filterChain.doFilter(request, response);
            return;
        }

        EndpointAccessRule rule = endpointRbacCacheService.match(request.getMethod(), request.getRequestURI());
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 禁用接口直接拦截（只针对已纳入 api_endpoints 的记录）
        if (rule.getStatus() != 1) {
            writeError(response, HttpStatus.FORBIDDEN, "接口已禁用");
            return;
        }

        RequestUserContext context = RequestUserContextHolder.get();
        boolean authenticated = context != null && context.isAuthenticated() && !context.isServiceCall();

        if (rule.isRequireAuth() && !authenticated) {
            writeError(response, HttpStatus.NOT_LOGIN, HttpMessage.NO_TOKEN);
            return;
        }

        // 未绑定权限：仅执行 require_auth 规则（是否需要登录）
        if (!rule.hasPermissionBindings()) {
            if (isStrictUnboundDenyEnabled() && rule.isRequireAuth()) {
                writeError(response, HttpStatus.FORBIDDEN, "接口未绑定权限，严格模式已拒绝访问");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (!authenticated) {
            writeError(response, HttpStatus.NOT_LOGIN, HttpMessage.NO_TOKEN);
            return;
        }

        if (matchesAnyPermission(rule, context)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeError(response, HttpStatus.FORBIDDEN, "权限不足，缺少接口访问权限");
    }

    private boolean matchesAnyPermission(EndpointAccessRule rule, RequestUserContext context) {
        if (context == null) {
            return false;
        }
        for (String permissionCode : rule.getPermissionCodes()) {
            if (context.hasPermission(permissionCode)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(Result.error(msg, status)));
    }

    private boolean isStrictUnboundDenyEnabled() {
        if (securityProperties == null || securityProperties.getEndpointRbac() == null) {
            return false;
        }
        Boolean enabled = securityProperties.getEndpointRbac().getStrictUnboundPermissionDeny();
        return enabled != null && enabled;
    }
}
