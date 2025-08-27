package com.ssy.handler;

import com.alibaba.fastjson.JSON;
import com.ssy.entity.HttpStatus;
import com.ssy.entity.Result;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/6
 * @email 3278440884@qq.com
 */

public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        // 这里可以自定义返回的状态码、响应格式等
        response.setStatus(HttpStatus.BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        // 自定义返回的消息

        response.getWriter().write(JSON.toJSONString( Result.error("无该权限")));
    }}
