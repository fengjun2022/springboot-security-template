package com.ssy.filter;

import com.alibaba.fastjson.JSON;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ssy.details.CustomUserDetails;
import com.ssy.dto.UserEntity;
import com.ssy.entity.HttpMessage;
import com.ssy.entity.HttpStatus;
import com.ssy.entity.Result;
import com.ssy.properties.JwtProperties;
import com.ssy.properties.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
@Order(2)
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

        // 3. 白名单以外，都要求用户Token
        String header = request.getHeader(jwtProperties.getHeadName());
        if (header == null || !header.startsWith(jwtProperties.getHeadBase())) {
            writeUnauthorizedResponse(response);
            return;
        }

        // 去掉前缀，获取实际的 Token
        String token = header.replace(jwtProperties.getHeadBase(), "");
        try {
            // 验证 Token 合法性
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
                user.setUserId(userId != null ? userId : 0L); // 提供默认值避免null
                user.setUsername(subject);
                user.setStatus(status != null ? status : 0);


                if (authoritiesClaim != null && !authoritiesClaim.isEmpty()) {
                    List<String> authorities = Arrays.stream(authoritiesClaim.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    user.setAuthorities(authorities);
                    System.err.println("用户权限: " + user.getAuthorities());
                }

                // 创建CustomUserDetails
                CustomUserDetails userDetails = new CustomUserDetails(user);

                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
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
}