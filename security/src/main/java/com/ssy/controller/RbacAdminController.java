package com.ssy.controller;

import com.ssy.dto.UserEntity;
import com.ssy.entity.RbacPermissionEntity;
import com.ssy.entity.RbacRoleEntity;
import com.ssy.entity.RbacRoleGrantRuleEntity;
import com.ssy.entity.Result;
import com.ssy.service.impl.RbacIdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/iam")
public class RbacAdminController {

    @Autowired
    private RbacIdentityService rbacIdentityService;

    @GetMapping("/me")
    public Result<UserEntity> me() {
        try {
            return Result.success(rbacIdentityService.sanitizeUser(rbacIdentityService.requireCurrentUser()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:user:read')")
    @GetMapping("/users")
    public Result<List<UserEntity>> listUsers(@RequestParam(defaultValue = "100") int limit) {
        try {
            return Result.success(rbacIdentityService.listRecentUsers(limit));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:user:read')")
    @GetMapping("/users/{userId}")
    public Result<UserEntity> getUser(@PathVariable Long userId) {
        try {
            UserEntity user = rbacIdentityService.getUserByUserId(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            return Result.success(rbacIdentityService.sanitizeUser(user));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:user:create')")
    @PostMapping("/users")
    public Result<UserEntity> createUser(@RequestBody CreateUserDTO dto) {
        try {
            RbacIdentityService.AdminCreateUserCommand cmd = new RbacIdentityService.AdminCreateUserCommand();
            cmd.setUsername(dto.getUsername());
            cmd.setPassword(dto.getPassword());
            cmd.setRoleCodes(dto.getRoleCodes());
            cmd.setStatus(dto.getStatus());
            return Result.success(rbacIdentityService.adminCreateUser(cmd));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:user:update')")
    @PutMapping("/users/{userId}")
    public Result<UserEntity> updateUser(@PathVariable Long userId, @RequestBody UpdateUserDTO dto) {
        try {
            RbacIdentityService.UpdateUserBasicCommand cmd = new RbacIdentityService.UpdateUserBasicCommand();
            cmd.setUsername(dto.getUsername());
            cmd.setPassword(dto.getPassword());
            cmd.setStatus(dto.getStatus());
            return Result.success(rbacIdentityService.updateUserBasic(userId, cmd));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:user:status:update')")
    @PatchMapping("/users/{userId}/status")
    public Result<UserEntity> updateUserStatus(@PathVariable Long userId, @RequestBody UpdateUserStatusDTO dto) {
        try {
            return Result.success(rbacIdentityService.updateUserStatus(userId, dto.getStatus()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:user:role:assign') and hasAuthority('iam:user:role:revoke')")
    @PutMapping("/users/{userId}/roles")
    public Result<UserEntity> replaceUserRoles(@PathVariable Long userId, @RequestBody ReplaceUserRolesDTO dto) {
        try {
            List<String> roleCodes = dto == null ? Collections.emptyList() : dto.getRoleCodes();
            return Result.success(rbacIdentityService.replaceUserRoles(userId, roleCodes));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:role:read')")
    @GetMapping("/roles")
    public Result<List<RbacRoleEntity>> listRoles() {
        try {
            return Result.success(rbacIdentityService.listRoles());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:role:create')")
    @PostMapping("/roles")
    public Result<RbacRoleEntity> createRole(@RequestBody CreateRoleDTO dto) {
        try {
            RbacIdentityService.CreateRoleCommand cmd = new RbacIdentityService.CreateRoleCommand();
            cmd.setRoleCode(dto.getRoleCode());
            cmd.setRoleName(dto.getRoleName());
            cmd.setStatus(dto.getStatus());
            cmd.setIsSystem(dto.getIsSystem());
            cmd.setAllowSelfRegister(dto.getAllowSelfRegister());
            cmd.setRemark(dto.getRemark());
            return Result.success(rbacIdentityService.createRole(cmd));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:role:update')")
    @PutMapping("/roles/{roleId}")
    public Result<RbacRoleEntity> updateRole(@PathVariable Long roleId, @RequestBody UpdateRoleDTO dto) {
        try {
            RbacIdentityService.UpdateRoleCommand cmd = new RbacIdentityService.UpdateRoleCommand();
            cmd.setRoleName(dto.getRoleName());
            cmd.setStatus(dto.getStatus());
            cmd.setAllowSelfRegister(dto.getAllowSelfRegister());
            cmd.setRemark(dto.getRemark());
            return Result.success(rbacIdentityService.updateRole(roleId, cmd));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:permission:read')")
    @GetMapping("/permissions")
    public Result<List<RbacPermissionEntity>> listPermissions() {
        try {
            return Result.success(rbacIdentityService.listPermissions());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:permission:create')")
    @PostMapping("/permissions")
    public Result<RbacPermissionEntity> createPermission(@RequestBody CreatePermissionDTO dto) {
        try {
            RbacIdentityService.CreatePermissionCommand cmd = new RbacIdentityService.CreatePermissionCommand();
            cmd.setPermCode(dto.getPermCode());
            cmd.setPermName(dto.getPermName());
            cmd.setModuleGroup(dto.getModuleGroup());
            cmd.setStatus(dto.getStatus());
            cmd.setRemark(dto.getRemark());
            return Result.success(rbacIdentityService.createPermission(cmd));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:permission:update')")
    @PutMapping("/permissions/{permissionId}")
    public Result<RbacPermissionEntity> updatePermission(@PathVariable Long permissionId, @RequestBody UpdatePermissionDTO dto) {
        try {
            RbacIdentityService.UpdatePermissionCommand cmd = new RbacIdentityService.UpdatePermissionCommand();
            cmd.setPermName(dto.getPermName());
            cmd.setModuleGroup(dto.getModuleGroup());
            cmd.setStatus(dto.getStatus());
            cmd.setRemark(dto.getRemark());
            return Result.success(rbacIdentityService.updatePermission(permissionId, cmd));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:role:read')")
    @GetMapping("/roles/{roleId}/permissions")
    public Result<List<RbacPermissionEntity>> listRolePermissions(@PathVariable Long roleId) {
        try {
            return Result.success(rbacIdentityService.listPermissionsByRole(roleId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:role:permission:bind')")
    @PutMapping("/roles/{roleId}/permissions")
    public Result<List<RbacPermissionEntity>> replaceRolePermissions(@PathVariable Long roleId, @RequestBody ReplaceRolePermissionsDTO dto) {
        try {
            List<String> permissionCodes = dto == null ? Collections.emptyList() : dto.getPermissionCodes();
            return Result.success(rbacIdentityService.replaceRolePermissions(roleId, permissionCodes));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:endpoint:permission:read')")
    @GetMapping("/endpoints/{endpointId}/permissions")
    public Result<List<RbacPermissionEntity>> listEndpointPermissions(@PathVariable Long endpointId) {
        try {
            return Result.success(rbacIdentityService.listPermissionsByEndpoint(endpointId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:endpoint:permission:bind')")
    @PutMapping("/endpoints/{endpointId}/permissions")
    public Result<List<RbacPermissionEntity>> replaceEndpointPermissions(@PathVariable Long endpointId,
                                                                         @RequestBody ReplaceEndpointPermissionsDTO dto) {
        try {
            List<String> permissionCodes = dto == null ? Collections.emptyList() : dto.getPermissionCodes();
            return Result.success(rbacIdentityService.replaceEndpointPermissions(endpointId, permissionCodes));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:endpoint:permission:bind')")
    @PutMapping("/endpoints/module-permissions")
    public Result<RbacIdentityService.ModuleEndpointPermissionBindResult> replaceModuleEndpointPermissions(
            @RequestBody ReplaceModuleEndpointPermissionsDTO dto) {
        try {
            if (dto == null) {
                return Result.error("请求参数不能为空");
            }
            return Result.success(rbacIdentityService.replaceModuleEndpointPermissions(
                    dto.getModuleGroup(),
                    dto.getPermissionCodes(),
                    dto.getOnlyEnabledEndpoints()
            ));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:grant-rule:read')")
    @GetMapping("/grant-rules")
    public Result<List<RbacRoleGrantRuleEntity>> listGrantRules() {
        try {
            return Result.success(rbacIdentityService.listGrantRules());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('iam:grant-rule:update')")
    @PutMapping("/grant-rules")
    public Result<String> replaceGrantRules(@RequestBody List<GrantRuleDTO> rules) {
        try {
            List<RbacIdentityService.GrantRuleUpsertCommand> commands = (rules == null ? Collections.<GrantRuleDTO>emptyList() : rules).stream().map(dto -> {
                RbacIdentityService.GrantRuleUpsertCommand cmd = new RbacIdentityService.GrantRuleUpsertCommand();
                cmd.setOperatorRoleCode(dto.getOperatorRoleCode());
                cmd.setTargetRoleCode(dto.getTargetRoleCode());
                cmd.setCanCreateUserWithRole(dto.getCanCreateUserWithRole());
                cmd.setCanAssignRole(dto.getCanAssignRole());
                cmd.setCanRevokeRole(dto.getCanRevokeRole());
                cmd.setCanUpdateUserOfRole(dto.getCanUpdateUserOfRole());
                cmd.setStatus(dto.getStatus());
                cmd.setRemark(dto.getRemark());
                return cmd;
            }).collect(java.util.stream.Collectors.toList());
            rbacIdentityService.replaceGrantRules(commands);
            return Result.success("角色授予规则更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    public static class CreateUserDTO {
        private String username;
        private String password;
        private List<String> roleCodes;
        private Integer status;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public List<String> getRoleCodes() { return roleCodes; }
        public void setRoleCodes(List<String> roleCodes) { this.roleCodes = roleCodes; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    public static class UpdateUserDTO {
        private String username;
        private String password;
        private Integer status;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    public static class UpdateUserStatusDTO {
        private Integer status;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    public static class ReplaceUserRolesDTO {
        private List<String> roleCodes;

        public List<String> getRoleCodes() { return roleCodes; }
        public void setRoleCodes(List<String> roleCodes) { this.roleCodes = roleCodes; }
    }

    public static class CreateRoleDTO {
        private String roleCode;
        private String roleName;
        private Integer status;
        private Integer isSystem;
        private Integer allowSelfRegister;
        private String remark;

        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Integer getIsSystem() { return isSystem; }
        public void setIsSystem(Integer isSystem) { this.isSystem = isSystem; }
        public Integer getAllowSelfRegister() { return allowSelfRegister; }
        public void setAllowSelfRegister(Integer allowSelfRegister) { this.allowSelfRegister = allowSelfRegister; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class UpdateRoleDTO {
        private String roleName;
        private Integer status;
        private Integer allowSelfRegister;
        private String remark;

        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Integer getAllowSelfRegister() { return allowSelfRegister; }
        public void setAllowSelfRegister(Integer allowSelfRegister) { this.allowSelfRegister = allowSelfRegister; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class CreatePermissionDTO {
        private String permCode;
        private String permName;
        private String moduleGroup;
        private Integer status;
        private String remark;

        public String getPermCode() { return permCode; }
        public void setPermCode(String permCode) { this.permCode = permCode; }
        public String getPermName() { return permName; }
        public void setPermName(String permName) { this.permName = permName; }
        public String getModuleGroup() { return moduleGroup; }
        public void setModuleGroup(String moduleGroup) { this.moduleGroup = moduleGroup; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class UpdatePermissionDTO {
        private String permName;
        private String moduleGroup;
        private Integer status;
        private String remark;

        public String getPermName() { return permName; }
        public void setPermName(String permName) { this.permName = permName; }
        public String getModuleGroup() { return moduleGroup; }
        public void setModuleGroup(String moduleGroup) { this.moduleGroup = moduleGroup; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class ReplaceRolePermissionsDTO {
        private List<String> permissionCodes;

        public List<String> getPermissionCodes() { return permissionCodes; }
        public void setPermissionCodes(List<String> permissionCodes) { this.permissionCodes = permissionCodes; }
    }

    public static class ReplaceEndpointPermissionsDTO {
        private List<String> permissionCodes;

        public List<String> getPermissionCodes() { return permissionCodes; }
        public void setPermissionCodes(List<String> permissionCodes) { this.permissionCodes = permissionCodes; }
    }

    public static class ReplaceModuleEndpointPermissionsDTO {
        private String moduleGroup;
        private List<String> permissionCodes;
        private Boolean onlyEnabledEndpoints;

        public String getModuleGroup() { return moduleGroup; }
        public void setModuleGroup(String moduleGroup) { this.moduleGroup = moduleGroup; }
        public List<String> getPermissionCodes() { return permissionCodes; }
        public void setPermissionCodes(List<String> permissionCodes) { this.permissionCodes = permissionCodes; }
        public Boolean getOnlyEnabledEndpoints() { return onlyEnabledEndpoints; }
        public void setOnlyEnabledEndpoints(Boolean onlyEnabledEndpoints) { this.onlyEnabledEndpoints = onlyEnabledEndpoints; }
    }

    public static class GrantRuleDTO {
        private String operatorRoleCode;
        private String targetRoleCode;
        private Integer canCreateUserWithRole;
        private Integer canAssignRole;
        private Integer canRevokeRole;
        private Integer canUpdateUserOfRole;
        private Integer status;
        private String remark;

        public String getOperatorRoleCode() { return operatorRoleCode; }
        public void setOperatorRoleCode(String operatorRoleCode) { this.operatorRoleCode = operatorRoleCode; }
        public String getTargetRoleCode() { return targetRoleCode; }
        public void setTargetRoleCode(String targetRoleCode) { this.targetRoleCode = targetRoleCode; }
        public Integer getCanCreateUserWithRole() { return canCreateUserWithRole; }
        public void setCanCreateUserWithRole(Integer canCreateUserWithRole) { this.canCreateUserWithRole = canCreateUserWithRole; }
        public Integer getCanAssignRole() { return canAssignRole; }
        public void setCanAssignRole(Integer canAssignRole) { this.canAssignRole = canAssignRole; }
        public Integer getCanRevokeRole() { return canRevokeRole; }
        public void setCanRevokeRole(Integer canRevokeRole) { this.canRevokeRole = canRevokeRole; }
        public Integer getCanUpdateUserOfRole() { return canUpdateUserOfRole; }
        public void setCanUpdateUserOfRole(Integer canUpdateUserOfRole) { this.canUpdateUserOfRole = canUpdateUserOfRole; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
