package com.ssy.context;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前请求线程内的用户上下文（用户端/服务端调用均可承载）。
 * 热路径只读，角色与权限预先转为 Set 方便 O(1) 判断。
 */
public class RequestUserContext {

    private final boolean authenticated;
    private final boolean serviceCall;
    private final Long userId;
    private final String username;
    private final Integer status;
    private final String loginType;
    private final String requestMethod;
    private final String requestUri;
    private final String clientIp;
    private final Set<String> roles;
    private final Set<String> permissions;

    private RequestUserContext(Builder builder) {
        this.authenticated = builder.authenticated;
        this.serviceCall = builder.serviceCall;
        this.userId = builder.userId;
        this.username = builder.username;
        this.status = builder.status;
        this.loginType = builder.loginType;
        this.requestMethod = builder.requestMethod;
        this.requestUri = builder.requestUri;
        this.clientIp = builder.clientIp;
        this.roles = freezeUpperCase(builder.roles);
        this.permissions = freeze(builder.permissions);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean isServiceCall() {
        return serviceCall;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Integer getStatus() {
        return status;
    }

    public String getLoginType() {
        return loginType;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public boolean hasRole(String roleCode) {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            return false;
        }
        String normalized = roleCode.trim();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        return roles.contains(normalized.toUpperCase());
    }

    public boolean hasPermission(String permissionCode) {
        if (permissionCode == null || permissionCode.trim().isEmpty()) {
            return false;
        }
        return permissions.contains(permissionCode.trim());
    }

    public Builder toBuilder() {
        return builder()
                .authenticated(this.authenticated)
                .serviceCall(this.serviceCall)
                .userId(this.userId)
                .username(this.username)
                .status(this.status)
                .loginType(this.loginType)
                .requestMethod(this.requestMethod)
                .requestUri(this.requestUri)
                .clientIp(this.clientIp)
                .roles(this.roles)
                .permissions(this.permissions);
    }

    public static Builder builder() {
        return new Builder();
    }

    private Set<String> freeze(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private Set<String> freezeUpperCase(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            String normalized = value.trim();
            if (normalized.startsWith("ROLE_")) {
                normalized = normalized.substring(5);
            }
            result.add(normalized.toUpperCase());
        }
        return Collections.unmodifiableSet(result);
    }

    public static class Builder {
        private boolean authenticated;
        private boolean serviceCall;
        private Long userId;
        private String username;
        private Integer status;
        private String loginType;
        private String requestMethod;
        private String requestUri;
        private String clientIp;
        private Set<String> roles = new LinkedHashSet<>();
        private Set<String> permissions = new LinkedHashSet<>();

        public Builder authenticated(boolean authenticated) {
            this.authenticated = authenticated;
            return this;
        }

        public Builder serviceCall(boolean serviceCall) {
            this.serviceCall = serviceCall;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder status(Integer status) {
            this.status = status;
            return this;
        }

        public Builder loginType(String loginType) {
            this.loginType = loginType;
            return this;
        }

        public Builder requestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
            return this;
        }

        public Builder requestUri(String requestUri) {
            this.requestUri = requestUri;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder roles(Iterable<String> roles) {
            this.roles.clear();
            if (roles != null) {
                for (String role : roles) {
                    if (role != null && !role.trim().isEmpty()) {
                        this.roles.add(role.trim());
                    }
                }
            }
            return this;
        }

        public Builder permissions(Iterable<String> permissions) {
            this.permissions.clear();
            if (permissions != null) {
                for (String permission : permissions) {
                    if (permission != null && !permission.trim().isEmpty()) {
                        this.permissions.add(permission.trim());
                    }
                }
            }
            return this;
        }

        public RequestUserContext build() {
            return new RequestUserContext(this);
        }
    }
}
