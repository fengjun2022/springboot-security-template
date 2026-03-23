package com.ssy.controller;

import com.common.result.Result;
import com.ssy.entity.AuditLogConfigEntity;
import com.ssy.properties.AuditLogProperties;
import com.ssy.properties.ThreatDetectionProperties;
import com.ssy.service.impl.AuditFieldDiffRecorderService;
import com.ssy.service.impl.AuditLogService;
import com.ssy.service.impl.ThreatRuntimeConfigService;
import com.ssy.mapper.UserMapper;
import com.ssy.dto.UserEntity;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "安全设置与审计")
@RestController
@RequestMapping("/security-admin")
public class SecurityOperationsController {

    private final ThreatDetectionProperties threatDetectionProperties;
    private final AuditLogProperties auditLogProperties;
    private final AuditLogService auditLogService;
    private final ThreatRuntimeConfigService threatRuntimeConfigService;
    private final AuditFieldDiffRecorderService auditFieldDiffRecorderService;
    private final UserMapper userMapper;

    public SecurityOperationsController(ThreatDetectionProperties threatDetectionProperties,
                                        AuditLogProperties auditLogProperties,
                                        AuditLogService auditLogService,
                                        ThreatRuntimeConfigService threatRuntimeConfigService,
                                        AuditFieldDiffRecorderService auditFieldDiffRecorderService,
                                        UserMapper userMapper) {
        this.threatDetectionProperties = threatDetectionProperties;
        this.auditLogProperties = auditLogProperties;
        this.auditLogService = auditLogService;
        this.threatRuntimeConfigService = threatRuntimeConfigService;
        this.auditFieldDiffRecorderService = auditFieldDiffRecorderService;
        this.userMapper = userMapper;
    }

    @ApiOperation("获取安全配置概览")
    @GetMapping("/config")
    @PreAuthorize("hasAuthority('security:settings:read') or hasAuthority('threat:admin:read') or hasAuthority('audit:log:read')")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("audit", auditLogService.getConfig());
        result.put("threatDetection", threatRuntimeConfigService.getCurrentConfig());
        result.put("auditDefaults", auditLogProperties);
        return Result.success(result);
    }

    @ApiOperation("更新异常监控配置")
    @PutMapping("/threat/config")
    @PreAuthorize("hasAuthority('threat:admin:manage')")
    public Result<ThreatDetectionProperties> updateThreatConfig(@RequestBody ThreatDetectionProperties payload) {
        ThreatDetectionProperties before = cloneThreatConfig(threatRuntimeConfigService.getCurrentConfig());
        ThreatDetectionProperties after = threatRuntimeConfigService.update(payload);
        auditFieldDiffRecorderService.recordSecurityDiff(
                "THREAT_DETECTION",
                "UPDATE_THREAT_CONFIG",
                "THREAT_CONFIG",
                "security_threat_config",
                before,
                after
        );
        return Result.success(after);
    }

    @ApiOperation("获取审计配置")
    @GetMapping("/audit/config")
    @PreAuthorize("hasAuthority('audit:log:read') or hasAuthority('security:settings:read')")
    public Result<AuditLogConfigEntity> getAuditConfig() {
        return Result.success(auditLogService.getConfig());
    }

    @ApiOperation("更新审计配置")
    @PutMapping("/audit/config")
    @PreAuthorize("hasAuthority('audit:log:manage')")
    public Result<AuditLogConfigEntity> updateAuditConfig(@RequestBody AuditLogConfigEntity payload) {
        AuditLogConfigEntity before = auditLogService.getConfig();
        AuditLogConfigEntity after = auditLogService.updateConfig(payload);
        auditFieldDiffRecorderService.recordSecurityDiff(
                "AUDIT",
                "UPDATE_AUDIT_CONFIG",
                "AUDIT_CONFIG",
                "security_audit_config",
                before,
                after
        );
        return Result.success(after);
    }

    @ApiOperation("获取审计统计")
    @GetMapping("/audit/stats")
    @PreAuthorize("hasAuthority('audit:log:read') or hasAuthority('security:settings:read')")
    public Result<Map<String, Object>> getAuditStats() {
        return Result.success(auditLogService.getStats());
    }

    @ApiOperation("分页查询审计日志")
    @GetMapping("/audit/logs")
    @PreAuthorize("hasAuthority('audit:log:read') or hasAuthority('security:settings:read')")
    public Result<AuditLogService.AuditLogPage> queryLogs(
            @RequestParam(defaultValue = "GLOBAL") String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(auditLogService.queryLogs(category, page, size, keyword));
    }

    @ApiOperation("分页查询被禁用用户")
    @GetMapping("/banned-users/page")
    @PreAuthorize("hasAuthority('security:settings:read') or hasAuthority('iam:user:read')")
    public Result<SimplePageResult<UserEntity>> bannedUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int offset = (safePage - 1) * safeSize;
        int total = userMapper.countDisabledUsers(keyword);
        List<UserEntity> records = userMapper.selectDisabledUsersByPage(offset, safeSize, keyword);
        return Result.success(new SimplePageResult<>(records, total, safePage, safeSize));
    }

    private ThreatDetectionProperties cloneThreatConfig(ThreatDetectionProperties source) {
        ThreatDetectionProperties target = new ThreatDetectionProperties();
        target.setEnabled(source.isEnabled());
        target.setMonitorUnknownEndpoints(source.isMonitorUnknownEndpoints());
        target.setTrustForwardHeaders(source.isTrustForwardHeaders());
        target.setCaptureBodySample(source.isCaptureBodySample());
        target.setMaxInspectBodyBytes(source.getMaxInspectBodyBytes());
        target.setGlobalWindowMs(source.getGlobalWindowMs());
        target.setGlobalWindowLimit(source.getGlobalWindowLimit());
        target.setEndpointWindowMs(source.getEndpointWindowMs());
        target.setEndpointWindowLimit(source.getEndpointWindowLimit());
        target.setAutoBlockSeconds(source.getAutoBlockSeconds());
        target.setAutoBlockMultiplier(source.getAutoBlockMultiplier());
        target.setAuthFeedbackWindowMs(source.getAuthFeedbackWindowMs());
        target.setAuth401FeedbackThreshold(source.getAuth401FeedbackThreshold());
        target.setAuth403FeedbackThreshold(source.getAuth403FeedbackThreshold());
        target.setAuth403AutoBlockThreshold(source.getAuth403AutoBlockThreshold());
        target.setEventQueueCapacity(source.getEventQueueCapacity());
        target.setBlacklistQueueCapacity(source.getBlacklistQueueCapacity());
        target.setDeviceRiskEnabled(source.isDeviceRiskEnabled());
        target.setDeviceRiskCaptchaScoreThreshold(source.getDeviceRiskCaptchaScoreThreshold());
        target.setDeviceRiskBlockScoreThreshold(source.getDeviceRiskBlockScoreThreshold());
        target.setDeviceRiskNewDeviceScore(source.getDeviceRiskNewDeviceScore());
        target.setDeviceRiskIpDriftScore(source.getDeviceRiskIpDriftScore());
        target.setDeviceRiskUaDriftScore(source.getDeviceRiskUaDriftScore());
        target.setDeviceRiskMultiAccountScore(source.getDeviceRiskMultiAccountScore());
        target.setDeviceRiskFailurePenalty(source.getDeviceRiskFailurePenalty());
        target.setDeviceRiskAccountSwitchWindowMs(source.getDeviceRiskAccountSwitchWindowMs());
        target.setDeviceRiskAccountSwitchThreshold(source.getDeviceRiskAccountSwitchThreshold());
        target.setScannerPathKeywords(source.getScannerPathKeywords() == null ? new ArrayList<>() : new ArrayList<>(source.getScannerPathKeywords()));
        return target;
    }

    public static class SimplePageResult<T> {
        private List<T> records;
        private long total;
        private int page;
        private int size;
        private int totalPages;

        public SimplePageResult(List<T> records, long total, int page, int size) {
            this.records = records;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = (int) Math.ceil(size == 0 ? 0 : (double) total / size);
        }

        public List<T> getRecords() {
            return records;
        }

        public long getTotal() {
            return total;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public int getTotalPages() {
            return totalPages;
        }
    }
}
