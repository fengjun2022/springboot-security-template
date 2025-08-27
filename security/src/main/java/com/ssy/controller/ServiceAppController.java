package com.ssy.controller;

import com.common.result.Result;
import com.ssy.dto.ServiceAppRegisterDTO;
import com.ssy.dto.ServiceAppUpdateDTO;
import com.ssy.entity.ServiceAppEntity;
import com.ssy.service.ServiceAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 服务应用管理Controller
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@RestController
@RequestMapping("/api/service-app")
public class ServiceAppController {

    @Autowired
    private ServiceAppService serviceAppService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register")
    public Result<ServiceAppEntity> registerApp(@RequestBody ServiceAppRegisterDTO registerDTO) {
        try {
            ServiceAppEntity serviceApp = serviceAppService.registerApp(
                    registerDTO.getAppName(),
                    registerDTO.getAllowedApis(),
                    registerDTO.getCreateBy(),
                    registerDTO.getRemark());
            return Result.success(serviceApp);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<ServiceAppEntity> getById(@PathVariable Long id) {
        ServiceAppEntity serviceApp = serviceAppService.getById(id);
        if (serviceApp != null) {
            return Result.success(serviceApp);
        } else {
            return Result.error("服务应用不存在");
        }
    }

    @GetMapping("/list")
    public Result<List<ServiceAppEntity>> getAllApps() {
        List<ServiceAppEntity> apps = serviceAppService.getAllApps();
        return Result.success(apps);
    }

    @GetMapping("/enabled")
    public Result<List<ServiceAppEntity>> getEnabledApps() {
        List<ServiceAppEntity> apps = serviceAppService.getAllEnabledApps();
        return Result.success(apps);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/update")
    public Result<ServiceAppEntity> updateApp(@RequestBody ServiceAppUpdateDTO updateDTO) {
        try {
            // 构建ServiceAppEntity
            ServiceAppEntity serviceApp = new ServiceAppEntity();
            serviceApp.setId(updateDTO.getId());
            serviceApp.setAppName(updateDTO.getAppName());
            serviceApp.setAllowedApiList(updateDTO.getAllowedApis());
            serviceApp.setUpdateBy(updateDTO.getUpdateBy());
            serviceApp.setRemark(updateDTO.getRemark());

            ServiceAppEntity updatedApp = serviceAppService.updateApp(serviceApp);
            return Result.success(updatedApp);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        try {
            serviceAppService.updateStatus(id, status);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteApp(@PathVariable Long id) {
        try {
            serviceAppService.deleteApp(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/validate")
    public Result<ServiceAppEntity> validateApp(
            @RequestParam String appId,
            @RequestParam String authCode) {
        ServiceAppEntity serviceApp = serviceAppService.validateApp(appId, authCode);
        if (serviceApp != null) {
            return Result.success(serviceApp);
        } else {
            return Result.error("应用验证失败");
        }
    }

    @GetMapping("/permission/check")
    public Result<Boolean> checkPermission(
            @RequestParam String appId,
            @RequestParam String apiPath) {
        boolean hasPermission = serviceAppService.hasPermission(appId, apiPath);
        return Result.success(hasPermission);
    }
}
