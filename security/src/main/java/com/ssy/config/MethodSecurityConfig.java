package com.ssy.config;

import com.ssy.security.ServiceCallVoter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.expression.method.ExpressionBasedPreInvocationAdvice;
import org.springframework.security.access.prepost.PreInvocationAuthorizationAdviceVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.access.vote.AuthenticatedVoter;
import org.springframework.security.access.vote.RoleVoter;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class MethodSecurityConfig extends GlobalMethodSecurityConfiguration {

    @Bean
    public PreInvocationAuthorizationAdviceVoter preInvocationAuthorizationAdviceVoter() {
        // 这是让 @PreAuthorize 表达式正常生效的关键 voter
        return new PreInvocationAuthorizationAdviceVoter(new ExpressionBasedPreInvocationAdvice());
    }

    @Override
    protected AccessDecisionManager accessDecisionManager() {
        List<AccessDecisionVoter<?>> voters = new ArrayList<>();
        voters.add(serviceCallMethodVoter());                 // 你的“服务调用直通”投票器（方法级版本）
        voters.add(preInvocationAuthorizationAdviceVoter());  // 让 @PreAuthorize 生效
        voters.add(new RoleVoter());                          // 角色投票
        voters.add(new AuthenticatedVoter());                 // 是否已认证
        return new AffirmativeBased(voters);
    }

    @Bean
    public AccessDecisionVoter<Object> serviceCallMethodVoter() {
        return new ServiceCallVoter(); // 下面把 supports/vote 写对
    }
}