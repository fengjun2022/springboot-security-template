package com.ssy.security;

import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

public class ServiceCallVoter implements AccessDecisionVoter<Object> {

    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    public boolean supports(ConfigAttribute attribute) {
        return true;
    }

    @Override
    public int vote(Authentication a, Object o, Collection<ConfigAttribute> atts) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        Object flag = (attrs != null) ? attrs.getRequest().getAttribute("SERVICE_CALL_VERIFIED") : null;
        boolean isService = a.getAuthorities().stream().anyMatch(x -> "ROLE_SERVICE".equals(x.getAuthority()));
        if (!isService) return ACCESS_ABSTAIN;                 // 普通用户/管理员：弃权
        return Boolean.TRUE.equals(flag) ? ACCESS_GRANTED : ACCESS_ABSTAIN; // 服务调用+验真：放行
    }
}