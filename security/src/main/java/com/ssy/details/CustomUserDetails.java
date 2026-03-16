package com.ssy.details;

import com.ssy.dto.UserEntity;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/3
 * @email 3278440884@qq.com
 */
@Data
public class CustomUserDetails implements UserDetails {
    private final UserEntity user;

    public CustomUserDetails(UserEntity user) {
        this.user = user;
    }

    // 这里返回用户对应的权限列表
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<String> merged = new LinkedHashSet<>();

        if (user.getRoles() != null) {
            for (String role : user.getRoles()) {
                if (role == null || role.trim().isEmpty()) {
                    continue;
                }
                String roleCode = role.trim();
                if (!roleCode.startsWith("ROLE_")) {
                    roleCode = "ROLE_" + roleCode;
                }
                merged.add(roleCode);
            }
        }

        if (user.getPermissions() != null) {
            for (String perm : user.getPermissions()) {
                if (perm == null || perm.trim().isEmpty()) {
                    continue;
                }
                merged.add(perm.trim());
            }
        }

        // 极少数旧数据兼容回退
        if (merged.isEmpty() && user.getAuthorities() != null) {
            for (String auth : user.getAuthorities()) {
                if (auth == null || auth.trim().isEmpty()) {
                    continue;
                }
                String value = auth.trim();
                if (!value.contains(":") && !value.startsWith("ROLE_")) {
                    value = "ROLE_" + value;
                }
                merged.add(value);
            }
        }

        if (merged.isEmpty()) {
            return Collections.emptyList();
        }

        return merged.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * TODO isAccountNonExpired isAccountNonLocked isCredentialsNonExpired isEnabled
     * 
     * @return
     */

    @Override
    public boolean isAccountNonExpired() {
        // 如果账户未过期，返回 true
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // 如果账户未被锁定，返回 true
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // 如果凭证未过期，返回 true
        return true;
    }

    @Override
    public boolean isEnabled() {

        return user.getStatus() == 0;
        // 判断用户账户是否处于"启用"状态

    }

    /**
     * 获取用户实体
     */
    public UserEntity getUser() {
        return user;
    }
}
