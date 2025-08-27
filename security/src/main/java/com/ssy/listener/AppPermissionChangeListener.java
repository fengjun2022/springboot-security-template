package com.ssy.listener;

import com.ssy.event.AppPermissionChangeEvent;
import com.ssy.service.PermissionCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用权限变更事件监听器
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/8/27
 */
@Component
public class AppPermissionChangeListener {

    @Autowired
    private PermissionCacheService permissionCacheService;

    @EventListener
    public void handleAppPermissionChange(AppPermissionChangeEvent event) {
        String appId = event.getAppId();
        AppPermissionChangeEvent.Type type = event.getType();

        switch (type) {
            case CREATE:
            case UPDATE:
            case ENABLE:
                // 创建、更新或启用时刷新缓存
                permissionCacheService.refreshAppPermission(appId);
                System.out.println("事件触发：刷新应用权限缓存，appId: " + appId + ", 操作类型: " + type);
                break;

            case DISABLE:
            case DELETE:
                // 禁用或删除时移除缓存
                permissionCacheService.removeAppPermission(appId);
                System.out.println("事件触发：移除应用权限缓存，appId: " + appId + ", 操作类型: " + type);
                break;
        }
    }
}