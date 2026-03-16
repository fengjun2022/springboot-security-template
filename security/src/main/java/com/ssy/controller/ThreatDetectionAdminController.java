package com.ssy.controller;

import com.common.result.Result;
import com.ssy.mapper.ApiEndpointMapper;
import com.ssy.entity.SecurityAttackEventEntity;
import com.ssy.entity.SecurityIpBlacklistEntity;
import com.ssy.entity.SecurityIpWhitelistEntity;
import com.ssy.mapper.SecurityAttackEventMapper;
import com.ssy.mapper.SecurityIpBlacklistMapper;
import com.ssy.mapper.SecurityIpWhitelistMapper;
import com.ssy.service.impl.EndpointThreatCacheService;
import com.ssy.service.impl.IpAccessControlService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常识别模块管理接口（第一阶段）。
 */
@Api(tags = "异常识别管理")
@RestController
@RequestMapping("/api/threat-detection")
@PreAuthorize("hasAuthority('threat:admin:read') or hasAuthority('threat:admin:manage')")
public class ThreatDetectionAdminController {

    private final SecurityAttackEventMapper securityAttackEventMapper;
    private final SecurityIpBlacklistMapper securityIpBlacklistMapper;
    private final SecurityIpWhitelistMapper securityIpWhitelistMapper;
    private final ApiEndpointMapper apiEndpointMapper;
    private final IpAccessControlService ipAccessControlService;
    private final EndpointThreatCacheService endpointThreatCacheService;

    public ThreatDetectionAdminController(SecurityAttackEventMapper securityAttackEventMapper,
                                          SecurityIpBlacklistMapper securityIpBlacklistMapper,
                                          SecurityIpWhitelistMapper securityIpWhitelistMapper,
                                          ApiEndpointMapper apiEndpointMapper,
                                          IpAccessControlService ipAccessControlService,
                                          EndpointThreatCacheService endpointThreatCacheService) {
        this.securityAttackEventMapper = securityAttackEventMapper;
        this.securityIpBlacklistMapper = securityIpBlacklistMapper;
        this.securityIpWhitelistMapper = securityIpWhitelistMapper;
        this.apiEndpointMapper = apiEndpointMapper;
        this.ipAccessControlService = ipAccessControlService;
        this.endpointThreatCacheService = endpointThreatCacheService;
    }

    @ApiOperation("获取异常识别缓存状态")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("endpointCacheSize", endpointThreatCacheService.size());
        stats.put("blacklistCacheSize", ipAccessControlService.blacklistSize());
        stats.put("whitelistCacheSize", ipAccessControlService.whitelistSize());
        return Result.success(stats);
    }

    @ApiOperation("查询最近攻击异常事件")
    @GetMapping("/events/recent")
    public Result<List<SecurityAttackEventEntity>> recentEvents(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return Result.success(securityAttackEventMapper.selectRecent(safeLimit));
    }

    @ApiOperation("分页筛选攻击异常事件（按IP/类型/时间范围）")
    @GetMapping("/events/page")
    public Result<PageResult<SecurityAttackEventEntity>> pageEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String attackType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int offset = (safePage - 1) * safeSize;

        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);

        int total = securityAttackEventMapper.countByCondition(ip, attackType, start, end);
        List<SecurityAttackEventEntity> records = securityAttackEventMapper.selectByPage(
                offset, safeSize, ip, attackType, start, end
        );
        return Result.success(new PageResult<>(records, total, safePage, safeSize));
    }

    @ApiOperation("查询黑名单IP")
    @GetMapping("/blacklist")
    public Result<List<SecurityIpBlacklistEntity>> blacklist(@RequestParam(defaultValue = "200") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        return Result.success(securityIpBlacklistMapper.selectRecent(safeLimit));
    }

    @ApiOperation("手动拉黑IP")
    @PreAuthorize("hasAuthority('threat:admin:manage')")
    @PostMapping("/blacklist")
    public Result<String> blockIp(@RequestBody BlockIpDTO dto) {
        if (dto == null || dto.getIp() == null || dto.getIp().trim().isEmpty()) {
            return Result.error("IP不能为空");
        }
        int expireSeconds = dto.getExpireSeconds() == null ? 3600 : Math.max(dto.getExpireSeconds(), 0);
        String reason = dto.getReason() == null || dto.getReason().trim().isEmpty() ? "管理员手动拉黑" : dto.getReason().trim();
        ipAccessControlService.manualBlockIp(dto.getIp().trim(), reason, expireSeconds);
        return Result.success("拉黑成功");
    }

    @ApiOperation("解除黑名单IP")
    @PreAuthorize("hasAuthority('threat:admin:manage')")
    @DeleteMapping("/blacklist")
    public Result<String> unblockIp(@RequestParam String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return Result.error("IP不能为空");
        }
        ipAccessControlService.removeFromBlacklist(ip.trim());
        return Result.success("解封成功");
    }

    @ApiOperation("查询白名单IP/CIDR")
    @GetMapping("/whitelist")
    public Result<List<SecurityIpWhitelistEntity>> whitelist(@RequestParam(defaultValue = "200") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        return Result.success(securityIpWhitelistMapper.selectRecent(safeLimit));
    }

    @ApiOperation("添加白名单IP/CIDR")
    @PreAuthorize("hasAuthority('threat:admin:manage')")
    @PostMapping("/whitelist")
    public Result<String> addWhitelist(@RequestBody WhitelistDTO dto) {
        if (dto == null || dto.getIpOrCidr() == null || dto.getIpOrCidr().trim().isEmpty()) {
            return Result.error("白名单IP或CIDR不能为空");
        }
        ipAccessControlService.addToWhitelist(dto.getIpOrCidr().trim(), dto.getRemark());
        return Result.success("添加白名单成功");
    }

    @ApiOperation("移除白名单IP/CIDR")
    @PreAuthorize("hasAuthority('threat:admin:manage')")
    @DeleteMapping("/whitelist")
    public Result<String> removeWhitelist(@RequestParam String ipOrCidr) {
        if (ipOrCidr == null || ipOrCidr.trim().isEmpty()) {
            return Result.error("白名单IP或CIDR不能为空");
        }
        ipAccessControlService.removeFromWhitelist(ipOrCidr.trim());
        return Result.success("移除白名单成功");
    }

    @ApiOperation("刷新异常识别缓存")
    @PreAuthorize("hasAuthority('threat:admin:manage')")
    @PostMapping("/cache/refresh")
    public Result<String> refreshCache() {
        endpointThreatCacheService.refresh();
        ipAccessControlService.refreshCaches();
        return Result.success("缓存刷新成功");
    }

    @ApiOperation("按模块批量开关接口异常识别监控")
    @PreAuthorize("hasAuthority('threat:admin:manage')")
    @PostMapping("/endpoint-monitor/module-toggle")
    public Result<String> batchToggleModuleMonitor(@RequestBody ModuleToggleDTO dto) {
        if (dto == null || dto.getModuleGroup() == null || dto.getModuleGroup().trim().isEmpty()) {
            return Result.error("模块分组不能为空");
        }
        if (dto.getThreatMonitorEnabled() == null || (dto.getThreatMonitorEnabled() != 0 && dto.getThreatMonitorEnabled() != 1)) {
            return Result.error("threatMonitorEnabled 只能是 0 或 1");
        }

        int affected = apiEndpointMapper.updateThreatMonitorByModule(
                dto.getModuleGroup().trim(),
                dto.getThreatMonitorEnabled(),
                LocalDateTime.now()
        );
        endpointThreatCacheService.refresh();
        return Result.success("批量更新完成，影响 " + affected + " 条接口记录");
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // fallback
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static class BlockIpDTO {
        private String ip;
        private String reason;
        private Integer expireSeconds;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Integer getExpireSeconds() {
            return expireSeconds;
        }

        public void setExpireSeconds(Integer expireSeconds) {
            this.expireSeconds = expireSeconds;
        }
    }

    public static class WhitelistDTO {
        private String ipOrCidr;
        private String remark;

        public String getIpOrCidr() {
            return ipOrCidr;
        }

        public void setIpOrCidr(String ipOrCidr) {
            this.ipOrCidr = ipOrCidr;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    public static class ModuleToggleDTO {
        private String moduleGroup;
        private Integer threatMonitorEnabled;

        public String getModuleGroup() {
            return moduleGroup;
        }

        public void setModuleGroup(String moduleGroup) {
            this.moduleGroup = moduleGroup;
        }

        public Integer getThreatMonitorEnabled() {
            return threatMonitorEnabled;
        }

        public void setThreatMonitorEnabled(Integer threatMonitorEnabled) {
            this.threatMonitorEnabled = threatMonitorEnabled;
        }
    }

    public static class PageResult<T> {
        private List<T> records;
        private long total;
        private int page;
        private int size;
        private int totalPages;

        public PageResult(List<T> records, long total, int page, int size) {
            this.records = records;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = (int) Math.ceil(size == 0 ? 0 : (double) total / size);
        }

        public List<T> getRecords() {
            return records;
        }

        public void setRecords(List<T> records) {
            this.records = records;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }
    }
}
