package com.ssy.event;

import org.springframework.context.ApplicationEvent;

/**
 * 应用权限变更事件
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/8/27
 */
public class AppPermissionChangeEvent extends ApplicationEvent {

    public enum Type {
        CREATE,     // 创建应用
        UPDATE,     // 更新权限
        ENABLE,     // 启用应用
        DISABLE,    // 禁用应用
        DELETE      // 删除应用
    }

    private final String appId;
    private final Type type;

    public AppPermissionChangeEvent(Object source, String appId, Type type) {
        super(source);
        this.appId = appId;
        this.type = type;
    }

    public String getAppId() {
        return appId;
    }

    public Type getType() {
        return type;
    }
}