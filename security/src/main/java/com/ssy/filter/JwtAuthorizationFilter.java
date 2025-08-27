package com.ssy.filter;

import com.alibaba.fastjson.JSON;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.ssy.details.CustomUserDetails;
import com.ssy.dto.UserEntity;
import com.ssy.entity.HttpMessage;
import com.ssy.entity.HttpStatus;
import com.ssy.entity.Result;
import com.ssy.properties.JwtProperties;
import com.ssy.properties.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.auth0.jwt.algorithms.Algorithm;

public class JwtAuthorizationFilter extends OncePerRequestFilter {
    @Autowired
    JwtProperties jwtProperties;
    @Autowired
    SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        response.setContentType("application/json;charset=UTF-8");
        System.out.println("requestURI" + requestURI);

        // 1. 如果在白名单中，直接放行
        if (isPermitAll(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 如果是服务间调用，跳过用户Token验证，由ServicePermissionFilter处理
        String serviceCallFlag = request.getHeader("X-Service-Call");
        if ("true".equals(serviceCallFlag)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 白名单以外，都要求Token (用户Token或管理员Token)
        String header = request.getHeader(jwtProperties.getHeadName());
        if (header == null || !header.startsWith(jwtProperties.getHeadBase())) {
            writeUnauthorizedResponse(response);
            return;
        }

        // 去掉前缀，获取实际的 Token
        String token = header.replace(jwtProperties.getHeadBase(), "");

        try {
            // 尝试验证用户Token
            boolean isUserTokenValid = validateUserToken(token);
            if (isUserTokenValid) {
                // 用户Token有效，设置用户认证信息
                setUserAuthentication(token);
            } else {
                // 尝试验证管理员Token
                boolean isAdminTokenValid = validateAdminToken(token);
                if (isAdminTokenValid) {
                    // 管理员Token有效，设置管理员认证信息
                    setAdminAuthentication(token);
                } else {
                    // 两个Token都无效
                    writeUnauthorizedResponse(response);
                    return;
                }
            }
        } catch (TokenExpiredException e) {
            response.setStatus(HttpStatus.NOT_LOGIN);
            response.getWriter().write(JSON.toJSONString(Result.error(HttpMessage.TOKEN_EXPIRED)));
            return;
        } catch (SignatureVerificationException e) {
            response.setStatus(HttpStatus.NOT_LOGIN);
            response.getWriter().write(JSON.toJSONString(Result.error(HttpMessage.TOKEN_INVALID)));
            return;
        } catch (AlgorithmMismatchException e) {
            response.setStatus(HttpStatus.NOT_LOGIN);
            response.getWriter().write(JSON.toJSONString(Result.error(HttpMessage.TOKEN_ALGORITHM_MISMATCH)));
            return;
        } catch (JWTVerificationException e) {
            response.setStatus(HttpStatus.BAD_REQUEST);
            response.getWriter().write(JSON.toJSONString(Result.error(HttpMessage.BAD_REQUEST + e.getMessage())));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPermitAll(String requestURI) {
        List<String> permitAllList = securityProperties.getPermitAll();
        return permitAllList.stream().anyMatch(pattern ->

        new AntPathMatcher().match(pattern, requestURI));
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        Result<Object> error = Result.error(HttpMessage.NO_TOKEN, HttpStatus.NOT_LOGIN);
        response.setStatus(HttpStatus.NOT_LOGIN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(error));
    }

    /**
     * 验证用户Token
     */
    private boolean validateUserToken(String token) {
        try {
            DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(jwtProperties.getSecretKey().getBytes()))
                    .build()
                    .verify(token);
            return decodedJWT.getSubject() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置用户认证信息
     */
    private void setUserAuthentication(String token) {
        try {
            DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(jwtProperties.getSecretKey().getBytes()))
                    .build()
                    .verify(token);
            String subject = decodedJWT.getSubject();

            if (subject != null) {
                // 从Token中提取用户信息，构建CustomUserDetails
                Long userId = decodedJWT.getClaim("userId").asLong();
                Integer status = decodedJWT.getClaim("status").asInt();
                String authoritiesClaim = decodedJWT.getClaim("authorities").asString();

                // 构建UserEntity
                UserEntity user = new UserEntity();
                user.setId(userId != null ? userId : 0L);
                user.setUsername(subject);
                user.setStatus(status != null ? status : 0);

                if (authoritiesClaim != null && !authoritiesClaim.isEmpty()) {
                    List<String> authorities = Arrays.asList(authoritiesClaim.split(","));
                    user.setAuthorities(authorities);
                }

                // 创建CustomUserDetails
                CustomUserDetails userDetails = new CustomUserDetails(user);

                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        } catch (Exception e) {
            // 验证失败，不设置认证信息
        }
    }

    /**
     * 验证管理员Token
     */
    private boolean validateAdminToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(getAdminSecretKey());
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);

            // 检查是否为管理员token
            String type = jwt.getClaim("type").asString();
            return "admin".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置管理员认证信息
     */
    private void setAdminAuthentication(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(getAdminSecretKey());
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);

            // 创建管理员认证信息
            String username = jwt.getSubject();
            String role = jwt.getClaim("role").asString();

            // 创建具有ADMIN角色的认证信息
            List<GrantedAuthority> authorities = Arrays.asList(
                    new SimpleGrantedAuthority("ROLE_" + role));

            // 创建管理员用户对象
            UserEntity adminUser = new UserEntity();
            adminUser.setId(-1L); // 管理员用户ID
            adminUser.setUsername(username);
            adminUser.setStatus(1); // 启用状态
            adminUser.setAuthorities(Arrays.asList(role));

            CustomUserDetails adminDetails = new CustomUserDetails(adminUser);

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    adminDetails, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        } catch (Exception e) {
            // 验证失败，不设置认证信息
        }
    }

    /**
     * 获取管理员密钥
     */
    private String getAdminSecretKey() {
        return "admin_jwt_secret_key_2025_zxy_hospital_system";
    }
}