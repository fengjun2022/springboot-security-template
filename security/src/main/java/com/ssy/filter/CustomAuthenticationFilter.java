package com.ssy.filter;

import com.alibaba.fastjson.JSON;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssy.constant.LoginRequest;
import com.ssy.details.CustomUserDetails;
import com.ssy.dto.UserEntity;
import com.ssy.entity.HttpMessage;
import com.ssy.entity.HttpStatus;
import com.ssy.entity.Result;
import com.ssy.properties.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * 支持多个登录URL的自定义认证过滤器
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/3
 * @email 3278440884@qq.com
 */
public class CustomAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;

    @Autowired
    JwtProperties jwtProperties;

    public CustomAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
        this.authenticationManager = authenticationManager;

        // 使用OrRequestMatcher支持多个登录URL
        OrRequestMatcher orMatcher = new OrRequestMatcher(
                new AntPathRequestMatcher("/login", "POST"),
                new AntPathRequestMatcher("/login-admin", "POST")
        );

        // 设置多路径匹配器
        setRequiresAuthenticationRequestMatcher(orMatcher);
    }

    // 从请求中解析 JSON 格式的用户名和密码
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        try {
            // 获取请求路径，可以在后续逻辑中区分用户类型
            String requestUri = request.getRequestURI();
            boolean isAdminLogin = requestUri.contains("admin");

            // 将请求 JSON 转换为 LoginRequest 对象（包含 username 和 password 字段）
            ObjectMapper mapper = new ObjectMapper();
            LoginRequest loginRequest = mapper.readValue(request.getInputStream(), LoginRequest.class);

            // 构造认证令牌，传入用户名和密码
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword());

            // 可以在这里添加额外的认证细节，比如用户类型
            if (isAdminLogin) {
                authenticationToken.setDetails("ADMIN_LOGIN");
            } else {
                authenticationToken.setDetails("USER_LOGIN");
            }

            // 调用 AuthenticationManager 进行认证
            return authenticationManager.authenticate(authenticationToken);
        } catch (IOException e) {
            throw new RuntimeException("登录字段错误" + e.getMessage());
        }
    }

    // 认证成功后生成 JWT Token，并写入响应头中
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                            Authentication authResult) throws IOException, ServletException {
//        SecurityContextHolder.getContext().setAuthentication(authResult);

        // 获取用户信息
        CustomUserDetails userDetails = (CustomUserDetails) authResult.getPrincipal();

        // 获取登录类型（可以根据需要在token中添加不同的claim）
        String loginType = (String) authResult.getDetails();

        // 生成权限字符串
        String authoritiesString = userDetails.getAuthorities().stream()
                .map(authority -> {
                    return authority.getAuthority();
                })
                .collect(Collectors.joining(","));


        String token = JWT.create()
                .withSubject(userDetails.getUsername())
                .withClaim("authorities", authoritiesString) // 使用简单的逗号分隔格式
                .withClaim("userId", userDetails.getUser().getUserId()) // 添加userId
                .withClaim("status", userDetails.getUser().getStatus()) // 添加status
                .withClaim("loginType", loginType)
                .withExpiresAt(new Date(System.currentTimeMillis() + jwtProperties.getTtl()))
                .sign(Algorithm.HMAC256(jwtProperties.getSecretKey().getBytes()));
        // 将 Token 写入响应头，前端需保存并在后续请求中携带该 Token
        response.addHeader(jwtProperties.getHeadName(), jwtProperties.getHeadBase() + token);
        UserEntity userDetailsUser = userDetails.getUser();
        userDetailsUser.setToken(token);
        userDetailsUser.setPassword(null);
        // 构建返回数据，Result.success 方法封装了成功状态和数据（此处返回用户名）
        Result<UserEntity> result = Result.success(userDetailsUser);

        // 设置响应类型为 JSON 并写入响应体
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(result));
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request,
                                              HttpServletResponse response,
                                              AuthenticationException failed)
            throws IOException, ServletException {
        // 设置 HTTP 状态码为 401，表示用户未通过认证
        response.setStatus(HttpStatus.NOT_LOGIN);
        // 设置响应类型为 JSON
        response.setContentType("application/json;charset=UTF-8");

        String errorMessage;

        // 根据异常类型返回不同的错误信息
        if (failed instanceof UsernameNotFoundException) {
            errorMessage = HttpMessage.LOGIN_USER_USERNAME_ERROR;
        } else if (failed instanceof BadCredentialsException) {
            errorMessage = HttpMessage.LOGIN_USER_PASSWORD_ERROR;
        } else {
            errorMessage = HttpMessage.AUTHENTICATION_FAILED;
        }

        // 可根据需要返回更丰富的信息，比如异常描述等
        Result<Object> error = Result.error(errorMessage, HttpStatus.NOT_LOGIN);
        response.getWriter().write(JSON.toJSONString(error));
    }
}