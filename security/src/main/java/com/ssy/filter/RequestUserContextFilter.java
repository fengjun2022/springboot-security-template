package com.ssy.filter;

import com.ssy.context.RequestUserContext;
import com.ssy.details.CustomUserDetails;
import com.ssy.dto.UserEntity;
import com.ssy.holder.RequestUserContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 将登录态解析成线程内上下文，便于业务层/过滤器热路径直接读取。
 */
public class RequestUserContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_USER_CONTEXT_ATTR = "REQUEST_USER_CONTEXT";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            RequestUserContext context = buildContext(request);
            if (context != null) {
                RequestUserContextHolder.set(context);
                request.setAttribute(REQUEST_USER_CONTEXT_ATTR, context);
            } else {
                RequestUserContextHolder.clear();
            }
            filterChain.doFilter(request, response);
        } finally {
            RequestUserContextHolder.clear();
        }
    }

    private RequestUserContext buildContext(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        RequestUserContext.Builder builder = RequestUserContext.builder()
                .requestMethod(request.getMethod())
                .requestUri(request.getRequestURI())
                .clientIp(resolveClientIp(request));

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return builder.authenticated(false).build();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            UserEntity user = ((CustomUserDetails) principal).getUser();
            return builder
                    .authenticated(true)
                    .serviceCall(false)
                    .userId(user.getUserId())
                    .username(user.getUsername())
                    .status(user.getStatus())
                    .loginType(user.getLoginType())
                    .roles(user.getRoles())
                    .permissions(user.getPermissions())
                    .build();
        }

        // 服务调用（ServicePermissionFilter注入的认证主体）
        String principalText = principal == null ? null : String.valueOf(principal);
        if (principalText != null && principalText.startsWith("SERVICE_")) {
            Set<String> roles = new LinkedHashSet<>();
            if (authentication.getAuthorities() != null) {
                for (GrantedAuthority authority : authentication.getAuthorities()) {
                    if (authority != null && authority.getAuthority() != null) {
                        roles.add(authority.getAuthority());
                    }
                }
            }
            return builder
                    .authenticated(true)
                    .serviceCall(true)
                    .username(principalText)
                    .roles(roles)
                    .build();
        }

        // 其他认证主体（保底）
        return builder
                .authenticated(true)
                .serviceCall(false)
                .username(principalText)
                .build();
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
