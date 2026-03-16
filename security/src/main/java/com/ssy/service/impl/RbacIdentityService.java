package com.ssy.service.impl;

import com.ssy.details.CustomUserDetails;
import com.ssy.dto.UserEntity;
import com.ssy.entity.ApiEndpointEntity;
import com.ssy.entity.RbacPermissionEntity;
import com.ssy.entity.RbacRoleEntity;
import com.ssy.entity.RbacRoleGrantRuleEntity;
import com.ssy.holder.RequestUserContextHolder;
import com.ssy.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RbacIdentityService {

    private final UserMapper userMapper;
    private final RbacRoleMapper rbacRoleMapper;
    private final RbacPermissionMapper rbacPermissionMapper;
    private final RbacPermissionEndpointRelMapper rbacPermissionEndpointRelMapper;
    private final RbacUserRoleMapper rbacUserRoleMapper;
    private final RbacRolePermissionMapper rbacRolePermissionMapper;
    private final RbacRoleGrantRuleMapper rbacRoleGrantRuleMapper;
    private final ApiEndpointMapper apiEndpointMapper;
    private final EndpointRbacCacheService endpointRbacCacheService;
    private final UserPermissionCacheService userPermissionCacheService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public RbacIdentityService(UserMapper userMapper,
                               RbacRoleMapper rbacRoleMapper,
                               RbacPermissionMapper rbacPermissionMapper,
                               RbacPermissionEndpointRelMapper rbacPermissionEndpointRelMapper,
                               RbacUserRoleMapper rbacUserRoleMapper,
                               RbacRolePermissionMapper rbacRolePermissionMapper,
                               RbacRoleGrantRuleMapper rbacRoleGrantRuleMapper,
                               ApiEndpointMapper apiEndpointMapper,
                               EndpointRbacCacheService endpointRbacCacheService,
                               UserPermissionCacheService userPermissionCacheService,
                               PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.rbacRoleMapper = rbacRoleMapper;
        this.rbacPermissionMapper = rbacPermissionMapper;
        this.rbacPermissionEndpointRelMapper = rbacPermissionEndpointRelMapper;
        this.rbacUserRoleMapper = rbacUserRoleMapper;
        this.rbacRolePermissionMapper = rbacRolePermissionMapper;
        this.rbacRoleGrantRuleMapper = rbacRoleGrantRuleMapper;
        this.apiEndpointMapper = apiEndpointMapper;
        this.endpointRbacCacheService = endpointRbacCacheService;
        this.userPermissionCacheService = userPermissionCacheService;
        this.passwordEncoder = passwordEncoder;
    }

    public UserEntity loadUserForAuth(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        UserEntity user = userMapper.queryUser(username.trim());
        if (user == null) {
            return null;
        }
        return enrichUserWithRbac(user);
    }

    public UserEntity getUserByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        UserEntity user = userMapper.selectByUserId(userId);
        if (user == null) {
            return null;
        }
        return enrichUserWithRbac(user);
    }

    public List<UserEntity> listRecentUsers(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<UserEntity> users = userMapper.selectRecent(safeLimit);
        for (UserEntity user : users) {
            enrichUserWithRbac(user);
            user.setPassword(null);
        }
        return users;
    }

    @Transactional
    public UserEntity selfRegister(SelfRegisterCommand cmd) {
        validateUsernameAndPassword(cmd.getUsername(), cmd.getPassword());
        ensureUsernameNotExists(cmd.getUsername());

        List<RbacRoleEntity> targetRoles = resolveRegisterRoles(cmd.getRoleCodes());
        for (RbacRoleEntity role : targetRoles) {
            if (!isTrue(role.getAllowSelfRegister())) {
                throw new IllegalArgumentException("角色不允许自注册: " + role.getRoleCode());
            }
        }

        UserEntity user = new UserEntity();
        user.setUsername(cmd.getUsername().trim());
        user.setPassword(passwordEncoder.encode(cmd.getPassword()));
        user.setStatus(cmd.getStatus() == null ? 0 : cmd.getStatus());
        userMapper.insertBaseUser(user);

        rbacUserRoleMapper.insertUserRoles(user.getUserId(), extractRoleIds(targetRoles), "SELF");
        userPermissionCacheService.invalidateUser(user.getUserId());

        UserEntity created = userMapper.selectByUserId(user.getUserId());
        return sanitizeUser(enrichUserWithRbac(created));
    }

    @Transactional
    public UserEntity adminCreateUser(AdminCreateUserCommand cmd) {
        UserEntity operator = requireCurrentUser();
        validateUsernameAndPassword(cmd.getUsername(), cmd.getPassword());
        ensureUsernameNotExists(cmd.getUsername());

        List<RbacRoleEntity> targetRoles = resolveRegisterRoles(cmd.getRoleCodes());
        ensureGrantAllowed(operator, targetRoles, GrantAction.CREATE_USER_WITH_ROLE);

        UserEntity user = new UserEntity();
        user.setUsername(cmd.getUsername().trim());
        user.setPassword(passwordEncoder.encode(cmd.getPassword()));
        user.setStatus(cmd.getStatus() == null ? 0 : cmd.getStatus());
        userMapper.insertBaseUser(user);
        rbacUserRoleMapper.insertUserRoles(user.getUserId(), extractRoleIds(targetRoles), "ADMIN");
        userPermissionCacheService.invalidateUser(user.getUserId());

        return sanitizeUser(enrichUserWithRbac(userMapper.selectByUserId(user.getUserId())));
    }

    @Transactional
    public UserEntity updateUserBasic(Long userId, UpdateUserBasicCommand cmd) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        UserEntity operator = requireCurrentUser();
        UserEntity target = userMapper.selectByUserId(userId);
        if (target == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        target = enrichUserWithRbac(target);
        ensureGrantAllowed(operator, resolveRolesByCodes(target.getRoles()), GrantAction.UPDATE_USER_OF_ROLE);

        if (StringUtils.hasText(cmd.getUsername())) {
            String newName = cmd.getUsername().trim();
            UserEntity exists = userMapper.queryUser(newName);
            if (exists != null && !Objects.equals(exists.getUserId(), userId)) {
                throw new IllegalArgumentException("用户名已存在");
            }
            target.setUsername(newName);
        }
        if (StringUtils.hasText(cmd.getPassword())) {
            target.setPassword(passwordEncoder.encode(cmd.getPassword()));
        } else {
            target.setPassword(null);
        }
        if (cmd.getStatus() != null) {
            target.setStatus(cmd.getStatus());
        }
        userMapper.updateBaseUser(target);
        userPermissionCacheService.invalidateUser(userId);

        return sanitizeUser(enrichUserWithRbac(userMapper.selectByUserId(userId)));
    }

    @Transactional
    public UserEntity updateUserStatus(Long userId, Integer status) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status不能为空");
        }
        UserEntity operator = requireCurrentUser();
        UserEntity target = userMapper.selectByUserId(userId);
        if (target == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        target = enrichUserWithRbac(target);
        ensureGrantAllowed(operator, resolveRolesByCodes(target.getRoles()), GrantAction.UPDATE_USER_OF_ROLE);
        userMapper.updateStatus(userId, status);
        userPermissionCacheService.invalidateUser(userId);
        return sanitizeUser(enrichUserWithRbac(userMapper.selectByUserId(userId)));
    }

    @Transactional
    public UserEntity replaceUserRoles(Long userId, List<String> roleCodes) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        UserEntity operator = requireCurrentUser();
        UserEntity target = userMapper.selectByUserId(userId);
        if (target == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        target = enrichUserWithRbac(target);

        List<RbacRoleEntity> newRoles = resolveRegisterRoles(roleCodes);
        List<RbacRoleEntity> currentRoles = resolveRolesByCodes(target.getRoles());

        Set<String> currentCodes = currentRoles.stream().map(RbacRoleEntity::getRoleCode).collect(Collectors.toSet());
        Set<String> newCodes = newRoles.stream().map(RbacRoleEntity::getRoleCode).collect(Collectors.toSet());

        List<RbacRoleEntity> toAssign = newRoles.stream()
                .filter(r -> !currentCodes.contains(r.getRoleCode()))
                .collect(Collectors.toList());
        List<RbacRoleEntity> toRevoke = currentRoles.stream()
                .filter(r -> !newCodes.contains(r.getRoleCode()))
                .collect(Collectors.toList());

        if (!toAssign.isEmpty()) {
            ensureGrantAllowed(operator, toAssign, GrantAction.ASSIGN_ROLE);
        }
        if (!toRevoke.isEmpty()) {
            ensureGrantAllowed(operator, toRevoke, GrantAction.REVOKE_ROLE);
        }

        rbacUserRoleMapper.deleteByUserId(userId);
        if (!newRoles.isEmpty()) {
            rbacUserRoleMapper.insertUserRoles(userId, extractRoleIds(newRoles), "ADMIN");
        }
        userPermissionCacheService.invalidateUser(userId);

        return sanitizeUser(enrichUserWithRbac(userMapper.selectByUserId(userId)));
    }

    public List<RbacRoleEntity> listRoles() {
        return rbacRoleMapper.selectAll();
    }

    @Transactional
    public RbacRoleEntity createRole(CreateRoleCommand cmd) {
        validateRoleCode(cmd.getRoleCode());
        if (rbacRoleMapper.selectByCode(cmd.getRoleCode().trim()) != null) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        RbacRoleEntity entity = new RbacRoleEntity();
        entity.setRoleCode(cmd.getRoleCode().trim().toUpperCase(Locale.ROOT));
        entity.setRoleName(requiredText(cmd.getRoleName(), "角色名称不能为空"));
        entity.setStatus(cmd.getStatus() == null ? 1 : cmd.getStatus());
        entity.setIsSystem(cmd.getIsSystem() == null ? 0 : cmd.getIsSystem());
        entity.setAllowSelfRegister(cmd.getAllowSelfRegister() == null ? 0 : cmd.getAllowSelfRegister());
        entity.setRemark(cmd.getRemark());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        rbacRoleMapper.insert(entity);
        userPermissionCacheService.invalidateAll();
        return rbacRoleMapper.selectById(entity.getId());
    }

    @Transactional
    public RbacRoleEntity updateRole(Long roleId, UpdateRoleCommand cmd) {
        RbacRoleEntity exists = rbacRoleMapper.selectById(roleId);
        if (exists == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        exists.setRoleName(StringUtils.hasText(cmd.getRoleName()) ? cmd.getRoleName().trim() : null);
        exists.setStatus(cmd.getStatus());
        exists.setAllowSelfRegister(cmd.getAllowSelfRegister());
        exists.setRemark(cmd.getRemark());
        exists.setUpdateTime(LocalDateTime.now());
        rbacRoleMapper.update(exists);
        userPermissionCacheService.invalidateAll();
        return rbacRoleMapper.selectById(roleId);
    }

    public List<RbacPermissionEntity> listPermissions() {
        return rbacPermissionMapper.selectAll();
    }

    @Transactional
    public RbacPermissionEntity createPermission(CreatePermissionCommand cmd) {
        validatePermissionCode(cmd.getPermCode());
        if (rbacPermissionMapper.selectByCode(cmd.getPermCode().trim()) != null) {
            throw new IllegalArgumentException("权限编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        RbacPermissionEntity entity = new RbacPermissionEntity();
        entity.setPermCode(cmd.getPermCode().trim());
        entity.setPermName(requiredText(cmd.getPermName(), "权限名称不能为空"));
        entity.setModuleGroup(cmd.getModuleGroup());
        entity.setStatus(cmd.getStatus() == null ? 1 : cmd.getStatus());
        entity.setRemark(cmd.getRemark());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        rbacPermissionMapper.insert(entity);
        endpointRbacCacheService.refresh();
        return rbacPermissionMapper.selectById(entity.getId());
    }

    @Transactional
    public RbacPermissionEntity updatePermission(Long permissionId, UpdatePermissionCommand cmd) {
        RbacPermissionEntity exists = rbacPermissionMapper.selectById(permissionId);
        if (exists == null) {
            throw new IllegalArgumentException("权限不存在");
        }
        exists.setPermName(StringUtils.hasText(cmd.getPermName()) ? cmd.getPermName().trim() : null);
        exists.setModuleGroup(cmd.getModuleGroup());
        exists.setStatus(cmd.getStatus());
        exists.setRemark(cmd.getRemark());
        exists.setUpdateTime(LocalDateTime.now());
        rbacPermissionMapper.update(exists);
        userPermissionCacheService.invalidateAll();
        endpointRbacCacheService.refresh();
        return rbacPermissionMapper.selectById(permissionId);
    }

    public List<RbacPermissionEntity> listPermissionsByRole(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        return rbacPermissionMapper.selectByRoleId(roleId);
    }

    @Transactional
    public List<RbacPermissionEntity> replaceRolePermissions(Long roleId, List<String> permissionCodes) {
        if (roleId == null) {
            throw new IllegalArgumentException("roleId不能为空");
        }
        RbacRoleEntity role = rbacRoleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        List<RbacPermissionEntity> permissions = resolvePermissionsByCodes(permissionCodes);
        rbacRolePermissionMapper.deleteByRoleId(roleId);
        if (!permissions.isEmpty()) {
            rbacRolePermissionMapper.insertRolePermissions(roleId, extractPermissionIds(permissions));
        }
        userPermissionCacheService.invalidateAll();
        return rbacPermissionMapper.selectByRoleId(roleId);
    }

    public List<RbacPermissionEntity> listPermissionsByEndpoint(Long endpointId) {
        if (endpointId == null) {
            return Collections.emptyList();
        }
        return rbacPermissionEndpointRelMapper.selectEnabledPermissionsByEndpointId(endpointId);
    }

    @Transactional
    public List<RbacPermissionEntity> replaceEndpointPermissions(Long endpointId, List<String> permissionCodes) {
        if (endpointId == null) {
            throw new IllegalArgumentException("endpointId不能为空");
        }
        if (apiEndpointMapper.selectById(endpointId) == null) {
            throw new IllegalArgumentException("接口不存在");
        }
        List<RbacPermissionEntity> permissions = resolvePermissionsByCodes(permissionCodes);
        rbacPermissionEndpointRelMapper.deleteByEndpointId(endpointId);
        if (!permissions.isEmpty()) {
            rbacPermissionEndpointRelMapper.insertEndpointPermissions(
                    endpointId,
                    extractPermissionIds(permissions),
                    "RBAC接口权限绑定",
                    LocalDateTime.now()
            );
        }
        endpointRbacCacheService.refresh();
        return rbacPermissionEndpointRelMapper.selectEnabledPermissionsByEndpointId(endpointId);
    }

    @Transactional
    public ModuleEndpointPermissionBindResult replaceModuleEndpointPermissions(String moduleGroup,
                                                                               List<String> permissionCodes,
                                                                               Boolean onlyEnabledEndpoints) {
        if (!StringUtils.hasText(moduleGroup)) {
            throw new IllegalArgumentException("moduleGroup不能为空");
        }
        boolean enabledOnly = onlyEnabledEndpoints == null || onlyEnabledEndpoints;
        List<ApiEndpointEntity> endpoints = apiEndpointMapper.selectByModuleGroup(moduleGroup.trim());
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("模块下未找到接口: " + moduleGroup);
        }

        List<ApiEndpointEntity> targetEndpoints = endpoints.stream()
                .filter(e -> !enabledOnly || (e.getStatus() != null && e.getStatus() == 1))
                .collect(Collectors.toList());

        List<RbacPermissionEntity> permissions = resolvePermissionsByCodes(permissionCodes);
        List<Long> permissionIds = extractPermissionIds(permissions);
        LocalDateTime now = LocalDateTime.now();

        int updatedEndpoints = 0;
        List<Long> endpointIds = new ArrayList<>();
        for (ApiEndpointEntity endpoint : targetEndpoints) {
            if (endpoint.getId() == null) {
                continue;
            }
            rbacPermissionEndpointRelMapper.deleteByEndpointId(endpoint.getId());
            if (!permissionIds.isEmpty()) {
                rbacPermissionEndpointRelMapper.insertEndpointPermissions(endpoint.getId(), permissionIds,
                        "按模块批量绑定权限", now);
            }
            updatedEndpoints++;
            endpointIds.add(endpoint.getId());
        }

        endpointRbacCacheService.refresh();
        return new ModuleEndpointPermissionBindResult(
                moduleGroup.trim(),
                enabledOnly,
                endpoints.size(),
                targetEndpoints.size(),
                updatedEndpoints,
                permissions.stream().map(RbacPermissionEntity::getPermCode).collect(Collectors.toList()),
                endpointIds
        );
    }

    public List<RbacRoleGrantRuleEntity> listGrantRules() {
        return rbacRoleGrantRuleMapper.selectAllWithRoleNames();
    }

    @Transactional
    public void replaceGrantRules(List<GrantRuleUpsertCommand> commands) {
        if (commands == null) {
            return;
        }
        Map<String, RbacRoleEntity> roleMap = rbacRoleMapper.selectAll().stream()
                .collect(Collectors.toMap(RbacRoleEntity::getRoleCode, Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        for (GrantRuleUpsertCommand cmd : commands) {
            String operatorRoleCode = normalizeRoleCode(cmd.getOperatorRoleCode());
            String targetRoleCode = normalizeRoleCode(cmd.getTargetRoleCode());
            RbacRoleEntity operatorRole = roleMap.get(operatorRoleCode);
            RbacRoleEntity targetRole = roleMap.get(targetRoleCode);
            if (operatorRole == null || targetRole == null) {
                throw new IllegalArgumentException("角色不存在: " + operatorRoleCode + " -> " + targetRoleCode);
            }
            RbacRoleGrantRuleEntity entity = new RbacRoleGrantRuleEntity();
            entity.setOperatorRoleId(operatorRole.getId());
            entity.setTargetRoleId(targetRole.getId());
            entity.setCanCreateUserWithRole(bool01(cmd.getCanCreateUserWithRole()));
            entity.setCanAssignRole(bool01(cmd.getCanAssignRole()));
            entity.setCanRevokeRole(bool01(cmd.getCanRevokeRole()));
            entity.setCanUpdateUserOfRole(bool01(cmd.getCanUpdateUserOfRole()));
            entity.setStatus(cmd.getStatus() == null ? 1 : cmd.getStatus());
            entity.setRemark(cmd.getRemark());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            rbacRoleGrantRuleMapper.upsert(entity);
        }
    }

    public UserEntity requireCurrentUser() {
        UserEntity contextUser = RequestUserContextHolder.toUserEntity();
        if (contextUser != null) {
            return contextUser;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("当前未登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            UserEntity user = ((CustomUserDetails) principal).getUser();
            if (user.getRoles() == null || user.getPermissions() == null) {
                return enrichUserWithRbac(userMapper.selectByUserId(user.getUserId()));
            }
            return user;
        }
        throw new IllegalStateException("当前登录主体不是系统用户");
    }

    public UserEntity enrichUserWithRbac(UserEntity user) {
        if (user == null || user.getUserId() == null) {
            return user;
        }
        List<RbacRoleEntity> roles = rbacRoleMapper.selectEnabledByUserId(user.getUserId());
        List<RbacPermissionEntity> permissions = rbacPermissionMapper.selectEnabledByUserId(user.getUserId());
        user.setRoles(roles.stream().map(RbacRoleEntity::getRoleCode).collect(Collectors.toList()));
        user.setPermissions(permissions.stream().map(RbacPermissionEntity::getPermCode).collect(Collectors.toList()));
        return user;
    }

    public UserEntity sanitizeUser(UserEntity user) {
        if (user != null) {
            user.setPassword(null);
            user.setAuthorities(null);
        }
        return user;
    }

    private void ensureUsernameNotExists(String username) {
        if (userMapper.countByUsername(username.trim()) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    private void validateUsernameAndPassword(String username, String password) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (username.trim().length() < 3) {
            throw new IllegalArgumentException("用户名长度不能小于3");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能小于6");
        }
    }

    private List<RbacRoleEntity> resolveRegisterRoles(List<String> roleCodes) {
        List<String> normalized = normalizeRoleCodes(roleCodes);
        if (normalized.isEmpty()) {
            normalized = Collections.singletonList("USER");
        }
        return resolveRolesByCodes(normalized);
    }

    private List<RbacRoleEntity> resolveRolesByCodes(Collection<String> roleCodes) {
        List<String> normalized = normalizeRoleCodes(roleCodes);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<RbacRoleEntity> roles = rbacRoleMapper.selectByCodes(normalized);
        if (roles.size() != new LinkedHashSet<>(normalized).size()) {
            Set<String> found = roles.stream().map(RbacRoleEntity::getRoleCode).collect(Collectors.toSet());
            List<String> missing = normalized.stream().filter(code -> !found.contains(code)).collect(Collectors.toList());
            throw new IllegalArgumentException("角色不存在: " + String.join(",", missing));
        }
        for (RbacRoleEntity role : roles) {
            if (!isTrue(role.getStatus())) {
                throw new IllegalArgumentException("角色已禁用: " + role.getRoleCode());
            }
        }
        return roles;
    }

    private List<RbacPermissionEntity> resolvePermissionsByCodes(Collection<String> permCodes) {
        List<String> normalized = normalizePermissionCodes(permCodes);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<RbacPermissionEntity> permissions = rbacPermissionMapper.selectByCodes(normalized);
        if (permissions.size() != new LinkedHashSet<>(normalized).size()) {
            Set<String> found = permissions.stream().map(RbacPermissionEntity::getPermCode).collect(Collectors.toSet());
            List<String> missing = normalized.stream().filter(code -> !found.contains(code)).collect(Collectors.toList());
            throw new IllegalArgumentException("权限不存在: " + String.join(",", missing));
        }
        for (RbacPermissionEntity permission : permissions) {
            if (!isTrue(permission.getStatus())) {
                throw new IllegalArgumentException("权限已禁用: " + permission.getPermCode());
            }
        }
        return permissions;
    }

    private List<Long> extractRoleIds(List<RbacRoleEntity> roles) {
        return roles.stream().map(RbacRoleEntity::getId).collect(Collectors.toList());
    }

    private List<Long> extractPermissionIds(List<RbacPermissionEntity> permissions) {
        return permissions.stream().map(RbacPermissionEntity::getId).collect(Collectors.toList());
    }

    private void ensureGrantAllowed(UserEntity operator, List<RbacRoleEntity> targetRoles, GrantAction action) {
        if (targetRoles == null || targetRoles.isEmpty()) {
            return;
        }
        List<String> operatorRoleCodes = operator.getRoles() == null ? Collections.emptyList() : new ArrayList<>(operator.getRoles());
        if (operatorRoleCodes.isEmpty()) {
            throw new IllegalArgumentException("当前账号没有角色，无法执行角色授权相关操作");
        }
        List<RbacRoleEntity> operatorRoles = resolveRolesByCodes(operatorRoleCodes);
        Map<String, RbacRoleGrantRuleEntity> ruleMap = rbacRoleGrantRuleMapper.selectEnabledWithRoleNames().stream()
                .collect(Collectors.toMap(
                        r -> r.getOperatorRoleId() + "_" + r.getTargetRoleId(),
                        Function.identity(),
                        (a, b) -> a
                ));

        for (RbacRoleEntity targetRole : targetRoles) {
            boolean allowed = false;
            for (RbacRoleEntity operatorRole : operatorRoles) {
                RbacRoleGrantRuleEntity rule = ruleMap.get(operatorRole.getId() + "_" + targetRole.getId());
                if (rule == null) {
                    continue;
                }
                if (actionAllowed(rule, action)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new IllegalArgumentException("当前账号无权对角色执行该操作: " + targetRole.getRoleCode() + " (" + action.name() + ")");
            }
        }
    }

    private boolean actionAllowed(RbacRoleGrantRuleEntity rule, GrantAction action) {
        switch (action) {
            case CREATE_USER_WITH_ROLE:
                return isTrue(rule.getCanCreateUserWithRole());
            case ASSIGN_ROLE:
                return isTrue(rule.getCanAssignRole());
            case REVOKE_ROLE:
                return isTrue(rule.getCanRevokeRole());
            case UPDATE_USER_OF_ROLE:
                return isTrue(rule.getCanUpdateUserOfRole());
            default:
                return false;
        }
    }

    private List<String> normalizeRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String code : roleCodes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String normalized = normalizeRoleCode(code);
            if (!normalized.isEmpty()) {
                set.add(normalized);
            }
        }
        return new ArrayList<>(set);
    }

    private String normalizeRoleCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        return normalized;
    }

    private List<String> normalizePermissionCodes(Collection<String> permissionCodes) {
        if (permissionCodes == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String code : permissionCodes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            set.add(code.trim());
        }
        return new ArrayList<>(set);
    }

    private void validateRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        String code = roleCode.trim();
        if (!code.matches("[A-Za-z0-9_:-]{2,64}")) {
            throw new IllegalArgumentException("角色编码格式不合法");
        }
    }

    private void validatePermissionCode(String permCode) {
        if (!StringUtils.hasText(permCode)) {
            throw new IllegalArgumentException("权限编码不能为空");
        }
        String code = permCode.trim();
        if (!code.matches("[A-Za-z0-9_:\\-\\.]{3,128}")) {
            throw new IllegalArgumentException("权限编码格式不合法");
        }
    }

    private String requiredText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private boolean isTrue(Integer v) {
        return v != null && v == 1;
    }

    private int bool01(Integer v) {
        return (v != null && v == 1) ? 1 : 0;
    }

    public enum GrantAction {
        CREATE_USER_WITH_ROLE,
        ASSIGN_ROLE,
        REVOKE_ROLE,
        UPDATE_USER_OF_ROLE
    }

    public static class SelfRegisterCommand {
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

    public static class AdminCreateUserCommand {
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

    public static class UpdateUserBasicCommand {
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

    public static class CreateRoleCommand {
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

    public static class UpdateRoleCommand {
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

    public static class CreatePermissionCommand {
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

    public static class UpdatePermissionCommand {
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

    public static class GrantRuleUpsertCommand {
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

    public static class ModuleEndpointPermissionBindResult {
        private final String moduleGroup;
        private final boolean onlyEnabledEndpoints;
        private final int moduleEndpointCount;
        private final int matchedEndpointCount;
        private final int updatedEndpointCount;
        private final List<String> permissionCodes;
        private final List<Long> endpointIds;

        public ModuleEndpointPermissionBindResult(String moduleGroup, boolean onlyEnabledEndpoints, int moduleEndpointCount,
                                                  int matchedEndpointCount, int updatedEndpointCount,
                                                  List<String> permissionCodes, List<Long> endpointIds) {
            this.moduleGroup = moduleGroup;
            this.onlyEnabledEndpoints = onlyEnabledEndpoints;
            this.moduleEndpointCount = moduleEndpointCount;
            this.matchedEndpointCount = matchedEndpointCount;
            this.updatedEndpointCount = updatedEndpointCount;
            this.permissionCodes = permissionCodes == null ? Collections.emptyList() : permissionCodes;
            this.endpointIds = endpointIds == null ? Collections.emptyList() : endpointIds;
        }

        public String getModuleGroup() {
            return moduleGroup;
        }

        public boolean isOnlyEnabledEndpoints() {
            return onlyEnabledEndpoints;
        }

        public int getModuleEndpointCount() {
            return moduleEndpointCount;
        }

        public int getMatchedEndpointCount() {
            return matchedEndpointCount;
        }

        public int getUpdatedEndpointCount() {
            return updatedEndpointCount;
        }

        public List<String> getPermissionCodes() {
            return permissionCodes;
        }

        public List<Long> getEndpointIds() {
            return endpointIds;
        }
    }
}
