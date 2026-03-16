package com.ssy.holder;

import com.ssy.context.RequestUserContext;
import com.ssy.dto.UserEntity;

import java.util.ArrayList;
import java.util.Optional;

/**
 * 请求线程内用户上下文持有器。
 */
public final class RequestUserContextHolder {

    private static final ThreadLocal<RequestUserContext> LOCAL = new ThreadLocal<>();

    private RequestUserContextHolder() {
    }

    public static void set(RequestUserContext context) {
        if (context == null) {
            LOCAL.remove();
            return;
        }
        LOCAL.set(context);
    }

    public static RequestUserContext get() {
        return LOCAL.get();
    }

    public static Optional<RequestUserContext> getOptional() {
        return Optional.ofNullable(LOCAL.get());
    }

    public static RequestUserContext require() {
        RequestUserContext context = LOCAL.get();
        if (context == null) {
            throw new IllegalStateException("当前线程不存在用户上下文");
        }
        return context;
    }

    public static void clear() {
        LOCAL.remove();
    }

    public static UserEntity toUserEntity() {
        RequestUserContext context = LOCAL.get();
        if (context == null || !context.isAuthenticated() || context.isServiceCall()) {
            return null;
        }
        UserEntity user = new UserEntity();
        user.setUserId(context.getUserId());
        user.setUsername(context.getUsername());
        user.setStatus(context.getStatus());
        user.setRoles(new ArrayList<>(context.getRoles()));
        user.setPermissions(new ArrayList<>(context.getPermissions()));
        return user;
    }
}
