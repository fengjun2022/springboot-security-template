package com.ssy.controller;

import com.common.result.Result;
import com.ssy.entity.ApiEndpointEntity;
import com.ssy.service.ApiEndpointService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API接口管理控制器
 * 提供API接口的查询、管理和扫描功能
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Api(tags = "API接口管理")
@RestController
@RequestMapping("/api/endpoints")
public class ApiEndpointController {

    @Autowired
    private ApiEndpointService apiEndpointService;

    /**
     * 分页查询API接口
     */
    @ApiOperation("分页查询API接口")
    @GetMapping("/page")
    public Result<ApiEndpointService.PageResult<ApiEndpointEntity>> getEndpointsByPage(
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") int size,
            @ApiParam("搜索关键词") @RequestParam(required = false) String keyword,
            @ApiParam("模块分组") @RequestParam(required = false) String moduleGroup) {
        try {
            ApiEndpointService.PageResult<ApiEndpointEntity> result = apiEndpointService.getEndpointsByPage(page, size,
                    keyword, moduleGroup);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 搜索API接口
     */
    @ApiOperation("搜索API接口")
    @GetMapping("/search")
    public Result<ApiEndpointService.PageResult<ApiEndpointEntity>> searchEndpoints(
            @ApiParam("搜索关键词") @RequestParam String keyword,
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") int size) {
        try {
            ApiEndpointService.PageResult<ApiEndpointEntity> result = apiEndpointService.getEndpointsByPage(page, size,
                    keyword, null);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有模块分组
     */
    @ApiOperation("获取所有模块分组")
    @GetMapping("/modules")
    public Result<List<String>> getAllModuleGroups() {
        try {
            List<String> modules = apiEndpointService.getAllModuleGroups();
            return Result.success(modules);
        } catch (Exception e) {
            return Result.error("获取模块分组失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询API接口详情
     */
    @ApiOperation("根据ID查询API接口详情")
    @GetMapping("/{id}")
    public Result<ApiEndpointEntity> getEndpointById(@ApiParam("接口ID") @PathVariable Long id) {
        try {
            ApiEndpointEntity endpoint = apiEndpointService.getEndpointById(id);
            if (endpoint != null) {
                return Result.success(endpoint);
            } else {
                return Result.error("接口不存在");
            }
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 更新API接口信息
     */
    @ApiOperation("更新API接口信息")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<String> updateEndpoint(
            @ApiParam("接口ID") @PathVariable Long id,
            @RequestBody ApiEndpointUpdateDTO updateDTO) {
        try {
            ApiEndpointEntity endpoint = new ApiEndpointEntity();
            endpoint.setId(id);
            endpoint.setDescription(updateDTO.getDescription());
            endpoint.setRequireAuth(updateDTO.getRequireAuth());
            endpoint.setModuleGroup(updateDTO.getModuleGroup());
            endpoint.setStatus(updateDTO.getStatus());
            endpoint.setRemark(updateDTO.getRemark());

            boolean success = apiEndpointService.updateEndpoint(endpoint);
            if (success) {
                return Result.success("更新成功");
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 手动扫描新增接口
     */
    @ApiOperation("手动扫描新增接口")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/scan")
    public Result<String> scanNewEndpoints() {
        try {
            int count = apiEndpointService.incrementalScanEndpoints();
            return Result.success("扫描完成，新增 " + count + " 个接口");
        } catch (Exception e) {
            return Result.error("扫描失败: " + e.getMessage());
        }
    }

    /**
     * 强制重新扫描所有接口
     */
    @ApiOperation("强制重新扫描所有接口")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/rescan")
    public Result<String> forceRescanEndpoints() {
        try {
            int count = apiEndpointService.forceRescanAllEndpoints();
            return Result.success("重新扫描完成，共 " + count + " 个接口");
        } catch (Exception e) {
            return Result.error("重新扫描失败: " + e.getMessage());
        }
    }

    /**
     * 按模块分组查询接口
     */
    @ApiOperation("按模块分组查询接口")
    @GetMapping("/by-module/{moduleGroup}")
    public Result<ApiEndpointService.PageResult<ApiEndpointEntity>> getEndpointsByModule(
            @ApiParam("模块分组") @PathVariable String moduleGroup,
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") int size) {
        try {
            ApiEndpointService.PageResult<ApiEndpointEntity> result = apiEndpointService.getEndpointsByPage(page, size,
                    null, moduleGroup);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * API接口更新DTO
     */
    public static class ApiEndpointUpdateDTO {
        private String description;
        private Integer requireAuth;
        private String moduleGroup;
        private Integer status;
        private String remark;

        // Getters and Setters
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getRequireAuth() {
            return requireAuth;
        }

        public void setRequireAuth(Integer requireAuth) {
            this.requireAuth = requireAuth;
        }

        public String getModuleGroup() {
            return moduleGroup;
        }

        public void setModuleGroup(String moduleGroup) {
            this.moduleGroup = moduleGroup;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
