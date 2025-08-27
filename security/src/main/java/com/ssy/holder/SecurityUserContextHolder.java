package com.ssy.holder;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;

@Component
public class SecurityUserContextHolder {

    /**
     * 获取当前认证信息
     *
     * @return 当前线程的 Authentication 对象，如果没有则返回 null
     */
    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 获取当前认证主体
     *
     * @return 当前认证的主体对象（通常是 UserDetails），如果没有认证则返回 null
     */
    public Object getPrincipal() {
        Authentication authentication = getAuthentication();
        return authentication != null ? authentication.getPrincipal() : null;
    }

    /**
     * 获取当前认证用户的用户名
     *
     * @return 如果认证成功，则返回用户名；否则返回 null
     */
    public String getUsername() {
        Object principal = getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else if (principal != null) {
            return principal.toString();
        }
        return null;
    }

    /**
     * 获取当前认证用户的权限列表
     *
     * @return 如果认证成功，则返回用户权限集合；否则返回一个空的权限列表
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Authentication authentication = getAuthentication();
        return authentication != null ? authentication.getAuthorities() : Collections.emptyList();
    }



    /**
     * 获取当前认证用户的UserDetails
     *
     * @return 如果认证成功，则返回用户名；否则返回 null
     */
    public UserDetails getUserDetails() {
        Object principal = getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal);
        }
        return null;
    }



}