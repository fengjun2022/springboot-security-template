package com.ssy.config;

import com.ssy.filter.CustomAuthenticationFilter;
import com.ssy.filter.JwtAuthorizationFilter;
import com.ssy.filter.TokenAutoHeaderFilter;
import com.ssy.handler.CustomAccessDeniedHandler;
import com.ssy.properties.SecurityProperties;
import com.ssy.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
// 假设你已经实现了这两个自定义过滤器，用于处理 JSON 登录和 JWT 授权

@Configuration
// 开启方法级安全权限访问控制
@EnableGlobalMethodSecurity(prePostEnabled = true, // 启用 @PreAuthorize 和 @PostAuthorize
        securedEnabled = true, // 启用 @Secured
        jsr250Enabled = true) // 启用 @RolesAllowed)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    // 声明密码编码器 Bean，用于加密和校验密码
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private final SecurityProperties securityProperties;

    @Bean
    public JwtAuthorizationFilter jwtAuthorizationFilter() {
        return new JwtAuthorizationFilter();
    }

    @Bean
    public CustomAuthenticationFilter customAuthenticationFilter() throws Exception {
        return new CustomAuthenticationFilter(authenticationManager());
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(CustomUserDetailsService customUserDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // 禁用隐藏用户不存在异常，允许抛出 UsernameNotFoundException
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    @Autowired
    public SecurityConfig(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // 禁用 CSRF（前后端分离项目通常不使用基于 Cookie 的 Session 认证）
        http.csrf().disable();
        // 配置 CORS 策略支持跨域请求
        http.cors().and();
        // 使用 URL 授权配置注册器统一配置所有 URL 的访问规则
        ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry = http
                .authorizeRequests();
        // 1. 加入允许所有人访问的 URL
        if (securityProperties.getPermitAll() != null) {
            for (String url : securityProperties.getPermitAll()) {
                registry.antMatchers(url).permitAll();
            }
        }

        // 2. 加入基于角色限制的 URL
        if (securityProperties.getRoleBased() != null) {
            for (SecurityProperties.RoleMapping mapping : securityProperties.getRoleBased()) {
                registry.antMatchers(mapping.getPattern()).hasRole(mapping.getRole());
            }
        }

        // 3. 其他所有请求均需要认证
        registry.anyRequest().authenticated();

        // 禁用默认的表单登录（前后端分离项目通常使用自定义认证方式）
        http.formLogin().disable();
        // 设置 Session 管理为无状态，不在服务端保存 Session 信息
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // 添加 Token 自动头部过滤器，在最前面处理token
        http.addFilterBefore(new TokenAutoHeaderFilter(), CustomAuthenticationFilter.class);
        // 添加自定义认证过滤器（处理 JSON 格式的登录请求）
        http.addFilter(customAuthenticationFilter());
        // 添加 JWT 授权过滤器，在认证过滤器之前拦截请求，根据请求头中的 JWT Token 进行授权验证
        http.addFilterBefore(jwtAuthorizationFilter(), CustomAuthenticationFilter.class);
        // 设置自定义的用户权限不足返回错误
        http.exceptionHandling().accessDeniedHandler(new CustomAccessDeniedHandler());
    }

    // 将 AuthenticationManager 暴露为 Bean，方便在自定义过滤器中注入使用
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}