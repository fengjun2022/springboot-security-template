package com.ssy.context;

import com.ssy.holder.RequestUserContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 可注入的当前用户访问器，避免业务代码直接写全限定名访问 ThreadLocal。
 *
 * 用法：
 * {@code @Autowired CurrentUser currentUser;}
 */
@Component
public class CurrentUser {

    public RequestUserContext ctx() {
        return RequestUserContextHolder.get();
    }

    public RequestUserContext require() {
        return RequestUserContextHolder.require();
    }

    public boolean isAuthenticated() {
        RequestUserContext ctx = RequestUserContextHolder.get();
        return ctx != null && ctx.isAuthenticated() && !ctx.isServiceCall();
    }

    public boolean isServiceCall() {
        RequestUserContext ctx = RequestUserContextHolder.get();
        return ctx != null && ctx.isServiceCall();
    }

    public Long userId() {
        RequestUserContext ctx = RequestUserContextHolder.get();
        if (ctx == null || !ctx.isAuthenticated() || ctx.isServiceCall()) {
            return null;
        }
        return ctx.getUserId();
    }

    public String username() {
        RequestUserContext ctx = RequestUserContextHolder.get();
        if (ctx == null) {
            return null;
        }
        return ctx.getUsername();
    }

    public Integer status() {
        RequestUserContext ctx = RequestUserContextHolder.get();
        if (ctx == null) {
            return null;
        }
        return ctx.getStatus();
    }

    public Set<String> roles() {
        RequestUserContext ctx = RequestUserContextHolder.get();
        return ctx == null ? Collections.emptySet() : ctx.getRoles();
    }

    public Set<String> permissions() {
        RequestUserContext ctx = RequestUserContextHolder.get();
        return ctx == null ? Collections.emptySet() : ctx.getPermissions();
    }

    public boolean hasRole(String roleCode) {
        RequestUserContext ctx = RequestUserContextHolder.get();
        return ctx != null && ctx.hasRole(roleCode);
    }

    public boolean hasPermission(String permissionCode) {
        RequestUserContext ctx = RequestUserContextHolder.get();
        return ctx != null && ctx.hasPermission(permissionCode);
    }
}
