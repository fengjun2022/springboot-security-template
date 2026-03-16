package com.ssy.service.impl;

import com.ssy.dto.UserEntity;
import com.ssy.entity.RbacPermissionEntity;
import com.ssy.entity.RbacRoleEntity;
import com.ssy.mapper.RbacPermissionMapper;
import com.ssy.mapper.RbacRoleMapper;
import com.ssy.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 用户权限本地缓存（单体版）
 * 目标：请求时只做本地读取，角色/权限变更后通过失效接口刷新。
 */
@Service
public class UserPermissionCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserPermissionCacheService.class);

    private final UserMapper userMapper;
    private final RbacRoleMapper rbacRoleMapper;
    private final RbacPermissionMapper rbacPermissionMapper;

    private final ConcurrentHashMap<Long, UserAuthSnapshot> cache = new ConcurrentHashMap<>();
    private final AtomicLong cacheVersion = new AtomicLong(0);

    public UserPermissionCacheService(UserMapper userMapper,
                                      RbacRoleMapper rbacRoleMapper,
                                      RbacPermissionMapper rbacPermissionMapper) {
        this.userMapper = userMapper;
        this.rbacRoleMapper = rbacRoleMapper;
        this.rbacPermissionMapper = rbacPermissionMapper;
    }

    public UserAuthSnapshot getUserSnapshot(Long userId) {
        if (userId == null) {
            return null;
        }
        return cache.computeIfAbsent(userId, this::loadSnapshot);
    }

    public void invalidateUser(Long userId) {
        if (userId == null) {
            return;
        }
        cache.remove(userId);
    }

    public void invalidateUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            if (userId != null) {
                cache.remove(userId);
            }
        }
    }

    public void invalidateAll() {
        cache.clear();
        long version = cacheVersion.incrementAndGet();
        log.info("用户权限缓存已清空，version={}", version);
    }

    public int size() {
        return cache.size();
    }

    private UserAuthSnapshot loadSnapshot(Long userId) {
        UserEntity user = userMapper.selectByUserId(userId);
        if (user == null) {
            return UserAuthSnapshot.notFound(userId);
        }

        List<RbacRoleEntity> roles = rbacRoleMapper.selectEnabledByUserId(userId);
        List<RbacPermissionEntity> permissions = rbacPermissionMapper.selectEnabledByUserId(userId);

        List<String> roleCodes = roles.stream()
                .map(RbacRoleEntity::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<String> permissionCodes = permissions.stream()
                .map(RbacPermissionEntity::getPermCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        return UserAuthSnapshot.of(userId, user.getUsername(), user.getStatus(), roleCodes, permissionCodes);
    }

    public static class UserAuthSnapshot {
        private final Long userId;
        private final String username;
        private final Integer status;
        private final Set<String> roles;
        private final Set<String> permissions;
        private final boolean exists;

        private UserAuthSnapshot(Long userId, String username, Integer status,
                                 Set<String> roles, Set<String> permissions, boolean exists) {
            this.userId = userId;
            this.username = username;
            this.status = status;
            this.roles = roles;
            this.permissions = permissions;
            this.exists = exists;
        }

        public static UserAuthSnapshot of(Long userId, String username, Integer status,
                                          Collection<String> roles, Collection<String> permissions) {
            return new UserAuthSnapshot(
                    userId,
                    username,
                    status,
                    roles == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(roles)),
                    permissions == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(permissions)),
                    true
            );
        }

        public static UserAuthSnapshot notFound(Long userId) {
            return new UserAuthSnapshot(userId, null, null, Collections.emptySet(), Collections.emptySet(), false);
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public Integer getStatus() {
            return status;
        }

        public Set<String> getRoles() {
            return roles;
        }

        public Set<String> getPermissions() {
            return permissions;
        }

        public boolean exists() {
            return exists;
        }

        /**
         * 与 CustomUserDetails#isEnabled 语义保持一致：status==0 为启用。
         */
        public boolean isEnabled() {
            return exists && status != null && status == 0;
        }
    }
}
