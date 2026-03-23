package com.ssy.controller;

import com.ssy.entity.Result;
import com.ssy.service.impl.LoginSecurityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/security-auth")
public class SecurityAuthController {

    private final LoginSecurityService loginSecurityService;

    public SecurityAuthController(LoginSecurityService loginSecurityService) {
        this.loginSecurityService = loginSecurityService;
    }

    @GetMapping("/captcha")
    public Result<LoginSecurityService.CaptchaPayload> getCaptcha(@RequestParam(required = false) String username,
                                                                  @RequestParam(required = false) String browserFingerprint,
                                                                  HttpServletRequest request) {
        return Result.success(loginSecurityService.getCaptcha(
                resolveClientIp(request),
                username,
                browserFingerprint,
                request.getHeader("User-Agent")
        ));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
