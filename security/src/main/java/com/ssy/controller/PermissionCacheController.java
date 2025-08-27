package com.ssy.controller;

import com.common.result.Result;
import com.ssy.service.PermissionCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限缓存管理Controller
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@RestController
@RequestMapping("/api/permission-cache")
public class PermissionCacheController {

    @Autowired
    private PermissionCacheService permissionCacheService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/init")
    public Result<Void> initCache() {
        try {
            permissionCacheService.initPermissionCache();
            return Result.success();
        } catch (Exception e) {
            return Result.error("初始化权限缓存失败: " + e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/refresh/{appId}")
    public Result<Void> refreshAppPermission(@PathVariable String appId) {
        try {
            permissionCacheService.refreshAppPermission(appId);
            return Result.success();
        } catch (Exception e) {
            return Result.error("刷新应用权限缓存失败: " + e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{appId}")
    public Result<Void> removeAppPermission(@PathVariable String appId) {
        try {
            permissionCacheService.removeAppPermission(appId);
            return Result.success();
        } catch (Exception e) {
            return Result.error("清除应用权限缓存失败: " + e.getMessage());
        }
    }

    @GetMapping("/{appId}/permissions")
    public Result<List<String>> getAppPermissions(@PathVariable String appId) {
        List<String> permissions = permissionCacheService.getAppPermissions(appId);
        return Result.success(permissions);
    }

    @GetMapping("/check")
    public Result<Boolean> checkPermission(
            @RequestParam String appId,
            @RequestParam String apiPath) {
        boolean hasPermission = permissionCacheService.hasPermission(appId, apiPath);
        return Result.success(hasPermission);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/clear")
    public Result<Void> clearAllCache() {
        try {
            permissionCacheService.clearAllCache();
            return Result.success();
        } catch (Exception e) {
            return Result.error("清除权限缓存失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public Result<String> getCacheStats() {
        String stats = permissionCacheService.getCacheStats();
        return Result.success(stats);
    }
}
