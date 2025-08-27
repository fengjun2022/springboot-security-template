package com.ssy.details;

import com.ssy.dto.UserEntity;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
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
        if (user.getAuthorities() != null) {
            return user.getAuthorities().stream()
                    .map(auth -> {
                        if (!auth.startsWith("ROLE_")) {
                            return new SimpleGrantedAuthority("ROLE_" + auth);
                        }
                        return new SimpleGrantedAuthority(auth);
                    })
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
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
