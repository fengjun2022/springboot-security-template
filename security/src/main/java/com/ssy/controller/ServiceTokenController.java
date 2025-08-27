package com.ssy.controller;

import com.common.result.Result;
import com.ssy.dto.ServiceTokenIssueDTO;
import com.ssy.entity.ServiceTokenEntity;
import com.ssy.service.ServiceTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 服务Token管理Controller
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@RestController
@RequestMapping("/api/service-token")
public class ServiceTokenController {

    @Autowired
    private ServiceTokenService serviceTokenService;

    @PostMapping("/issue")
    public Result<ServiceTokenEntity> issueToken(@RequestBody ServiceTokenIssueDTO issueDTO) {
        try {
            ServiceTokenEntity serviceToken = serviceTokenService.issueToken(
                    issueDTO.getAppId(),
                    issueDTO.getAuthCode(),
                    issueDTO.getIssueBy());
            return Result.success(serviceToken);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/validate")
    public Result<ServiceTokenEntity> validateToken(@RequestParam String token) {
        ServiceTokenEntity serviceToken = serviceTokenService.validateToken(token);
        if (serviceToken != null) {
            return Result.success(serviceToken);
        } else {
            return Result.error("token验证失败");
        }
    }

    @GetMapping("/app/{appId}")
    public Result<ServiceTokenEntity> getTokenByAppId(@PathVariable String appId) {
        ServiceTokenEntity serviceToken = serviceTokenService.getTokenByAppId(appId);
        if (serviceToken != null) {
            return Result.success(serviceToken);
        } else {
            return Result.error("未找到该应用的有效token");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{tokenId}/invalidate")
    public Result<Void> invalidateToken(@PathVariable Long tokenId) {
        try {
            serviceTokenService.invalidateToken(tokenId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/regenerate")
    public Result<ServiceTokenEntity> regenerateToken(@RequestBody ServiceTokenIssueDTO issueDTO) {
        try {
            ServiceTokenEntity serviceToken = serviceTokenService.regenerateToken(
                    issueDTO.getAppId(),
                    issueDTO.getAuthCode(),
                    issueDTO.getIssueBy());
            return Result.success(serviceToken);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/app/{appId}/invalidate")
    public Result<Void> invalidateTokensByAppId(@PathVariable String appId) {
        try {
            serviceTokenService.invalidateTokensByAppId(appId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
