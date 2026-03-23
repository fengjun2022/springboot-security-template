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
import com.ssy.entity.SecurityAttackEventEntity;
import com.ssy.properties.JwtProperties;
import com.ssy.service.impl.AttackEventAsyncRecorderService;
import com.ssy.service.impl.LoginSecurityService;
import com.ssy.service.impl.PacketFingerprintService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.util.StringUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final String ATTR_LOGIN_REQUEST = "SECURITY_LOGIN_REQUEST";
    private static final String ATTR_LOGIN_FAILURE_PAYLOAD = "SECURITY_LOGIN_FAILURE_PAYLOAD";

    private final AuthenticationManager authenticationManager;
    private final JwtProperties jwtProperties;
    private final LoginSecurityService loginSecurityService;
    private final AttackEventAsyncRecorderService attackEventAsyncRecorderService;
    private final PacketFingerprintService packetFingerprintService;

    public CustomAuthenticationFilter(AuthenticationManager authenticationManager,
                                      JwtProperties jwtProperties,
                                      LoginSecurityService loginSecurityService,
                                      AttackEventAsyncRecorderService attackEventAsyncRecorderService,
                                      PacketFingerprintService packetFingerprintService) {
        super(authenticationManager);
        this.authenticationManager = authenticationManager;
        this.jwtProperties = jwtProperties;
        this.loginSecurityService = loginSecurityService;
        this.attackEventAsyncRecorderService = attackEventAsyncRecorderService;
        this.packetFingerprintService = packetFingerprintService;

        OrRequestMatcher orMatcher = new OrRequestMatcher(
                new AntPathRequestMatcher("/login", "POST"),
                new AntPathRequestMatcher("/login-admin", "POST")
        );
        setRequiresAuthenticationRequestMatcher(orMatcher);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        try {
            String requestUri = request.getRequestURI();
            boolean isAdminLogin = requestUri.contains("admin");
            ObjectMapper mapper = new ObjectMapper();
            LoginRequest loginRequest = mapper.readValue(request.getInputStream(), LoginRequest.class);
            request.setAttribute(ATTR_LOGIN_REQUEST, loginRequest);

            LoginSecurityService.ValidationResult validationResult = loginSecurityService.validateBeforeLogin(
                    resolveClientIp(request),
                    loginRequest.getUsername(),
                    loginRequest.getCaptchaToken(),
                    loginRequest.getCaptchaCode(),
                    loginRequest.getBrowserFingerprint(),
                    request.getHeader("User-Agent")
            );
            if (!validationResult.isPass()) {
                request.setAttribute(ATTR_LOGIN_FAILURE_PAYLOAD, validationResult.getCaptchaPayload());
                throw new AuthenticationServiceException(validationResult.getReason());
            }

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword());
            authenticationToken.setDetails(isAdminLogin ? "ADMIN_LOGIN" : "USER_LOGIN");
            return authenticationManager.authenticate(authenticationToken);
        } catch (IOException e) {
            throw new RuntimeException("登录字段错误" + e.getMessage());
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                            Authentication authResult) throws IOException, ServletException {
        CustomUserDetails userDetails = (CustomUserDetails) authResult.getPrincipal();
        UserEntity userDetailsUser = userDetails.getUser();
        String loginType = (String) authResult.getDetails();

        loginSecurityService.onLoginSuccess(
                resolveClientIp(request),
                userDetails.getUsername(),
                resolveLoginRequest(request) == null ? null : resolveLoginRequest(request).getBrowserFingerprint(),
                request.getHeader("User-Agent")
        );

        Collection<String> roleCodes = userDetailsUser.getRoles() == null ? Collections.emptyList() : userDetailsUser.getRoles();
        Collection<String> permissionCodes = userDetailsUser.getPermissions() == null ? Collections.emptyList() : userDetailsUser.getPermissions();

        String rolesClaim = roleCodes.stream()
                .filter(x -> x != null && !x.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.joining(","));
        String permissionsClaim = permissionCodes.stream()
                .filter(x -> x != null && !x.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.joining(","));

        String token = JWT.create()
                .withSubject(userDetails.getUsername())
                .withClaim("roles", rolesClaim)
                .withClaim("permissions", permissionsClaim)
                .withClaim("userId", userDetails.getUser().getUserId())
                .withClaim("status", userDetails.getUser().getStatus())
                .withClaim("loginType", loginType)
                .withExpiresAt(new Date(System.currentTimeMillis() + jwtProperties.getTtl()))
                .sign(Algorithm.HMAC256(jwtProperties.getSecretKey().getBytes()));

        userDetailsUser.setPacketSecret(packetFingerprintService.issueSecret(token, System.currentTimeMillis() + jwtProperties.getTtl()));

        response.addHeader(jwtProperties.getHeadName(), jwtProperties.getHeadBase() + token);
        userDetailsUser.setToken(token);
        userDetailsUser.setPassword(null);
        Result<UserEntity> result = Result.success(userDetailsUser);

        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(result));
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request,
                                              HttpServletResponse response,
                                              AuthenticationException failed)
            throws IOException, ServletException {
        response.setStatus(HttpStatus.NOT_LOGIN);
        response.setContentType("application/json;charset=UTF-8");

        LoginRequest loginRequest = (LoginRequest) request.getAttribute(ATTR_LOGIN_REQUEST);
        LoginSecurityService.FailureResult failureResult = loginSecurityService.onLoginFailure(
                resolveClientIp(request),
                loginRequest == null ? null : loginRequest.getUsername(),
                loginRequest == null ? null : loginRequest.getPassword(),
                loginRequest == null ? null : loginRequest.getBrowserFingerprint(),
                request.getHeader("User-Agent")
        );

        String errorMessage;
        if (failed instanceof AuthenticationServiceException && "CAPTCHA_REQUIRED".equals(failed.getMessage())) {
          errorMessage = "连续失败次数过多，请先完成验证码校验";
        } else if (failed instanceof AuthenticationServiceException && "CAPTCHA_INVALID".equals(failed.getMessage())) {
          errorMessage = "验证码错误或已过期，请重新输入";
        } else if (failed instanceof AuthenticationServiceException && "DEVICE_RISK_BLOCKED".equals(failed.getMessage())) {
            errorMessage = "当前设备风险过高，已拒绝本次登录";
        } else if (failed instanceof DisabledException) {
            errorMessage = "账号已被封禁或禁用";
        } else if (failed instanceof LockedException) {
            errorMessage = "账号已被锁定";
        } else if (failed instanceof UsernameNotFoundException) {
            errorMessage = HttpMessage.LOGIN_USER_USERNAME_ERROR;
        } else if (failed instanceof BadCredentialsException) {
            errorMessage = HttpMessage.LOGIN_USER_PASSWORD_ERROR;
        } else {
            errorMessage = HttpMessage.AUTHENTICATION_FAILED;
        }

        // 弱口令提示：不覆盖验证码/设备风险等系统级拦截消息，避免用户误以为是密码被拒绝
        boolean isSystemBlock = failed instanceof AuthenticationServiceException
                && ("CAPTCHA_REQUIRED".equals(failed.getMessage())
                    || "CAPTCHA_INVALID".equals(failed.getMessage())
                    || "DEVICE_RISK_BLOCKED".equals(failed.getMessage()));
        if (failureResult.isWeakPassword() && !isSystemBlock) {
            errorMessage = "密码错误，且该密码属于弱口令，请登录后及时修改密码";
        }

        @SuppressWarnings("unchecked")
        LoginSecurityService.CaptchaPayload requestCaptchaPayload =
                (LoginSecurityService.CaptchaPayload) request.getAttribute(ATTR_LOGIN_FAILURE_PAYLOAD);
        LoginSecurityService.CaptchaPayload captchaPayload =
                requestCaptchaPayload != null
                        ? requestCaptchaPayload
                        : failureResult.getCaptchaPayload();

        recordLoginFailureEvent(request, loginRequest, failureResult, errorMessage);

        Map<String, Object> payload = new HashMap<>();
        payload.put("captchaRequired", captchaPayload != null && captchaPayload.isCaptchaRequired());
        payload.put("failureCount", failureResult.getFailureCount());
        if (captchaPayload != null && captchaPayload.isCaptchaRequired()) {
            payload.put("captchaToken", captchaPayload.getCaptchaToken());
            payload.put("captchaSvg", captchaPayload.getCaptchaSvg());
        }
        if (captchaPayload != null) {
            payload.put("deviceRiskEnabled", captchaPayload.isDeviceRiskEnabled());
            payload.put("deviceRiskScore", captchaPayload.getDeviceRiskScore());
            payload.put("riskLevel", captchaPayload.getRiskLevel());
            payload.put("riskReasons", captchaPayload.getRiskReasons());
            payload.put("blockLogin", captchaPayload.isBlockLogin());
            payload.put("trustedBrowser", captchaPayload.isTrustedBrowser());
            payload.put("challengeMessage", captchaPayload.getChallengeMessage());
        }

        Result<Object> error = Result.error(errorMessage, HttpStatus.NOT_LOGIN, payload);
        response.getWriter().write(JSON.toJSONString(error));
    }

    private void recordLoginFailureEvent(HttpServletRequest request,
                                         LoginRequest loginRequest,
                                         LoginSecurityService.FailureResult failureResult,
                                         String errorMessage) {
        SecurityAttackEventEntity event = new SecurityAttackEventEntity();
        event.setIp(resolveClientIp(request));
        event.setAttackType(failureResult.getAttackType());
        event.setPath(request.getRequestURI());
        event.setMethod(request.getMethod());
        event.setUsername(loginRequest == null ? null : loginRequest.getUsername());
        event.setClientTool(resolveClientTool(request.getHeader("User-Agent")));
        event.setBrowserFingerprint(loginRequest == null ? null : loginRequest.getBrowserFingerprint());
        event.setBrowserTrusted(resolveBrowserTrusted(request.getHeader("User-Agent"),
                loginRequest == null ? null : loginRequest.getBrowserFingerprint()));
        event.setUserAgent(request.getHeader("User-Agent"));
        event.setRiskScore(Math.max(
                failureResult.getAssessment() == null ? 0 : failureResult.getAssessment().getRiskScore(),
                failureResult.getFailureCount() >= 5 ? 88 : 60
        ));
        event.setBlockAction(failureResult.getCaptchaPayload() != null && failureResult.getCaptchaPayload().isBlockLogin()
                ? "BLOCK"
                : failureResult.getFailureCount() >= 5 ? "CAPTCHA_REQUIRED" : "LOG_ONLY");
        event.setBlockReason(errorMessage);
        event.setSuggestedAction(failureResult.getFailureCount() >= 5 ? "继续失败将触发验证码与拉黑策略" : "观察该账号/IP后续登录行为");
        event.setCreateTime(LocalDateTime.now());
        attackEventAsyncRecorderService.record(event);
    }

    private LoginRequest resolveLoginRequest(HttpServletRequest request) {
        return (LoginRequest) request.getAttribute(ATTR_LOGIN_REQUEST);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveClientTool(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "UNKNOWN";
        }
        String normalized = userAgent.toLowerCase();
        if (normalized.contains("sqlmap")) {
            return "SQLMAP";
        }
        if (normalized.contains("curl")) {
            return "CURL";
        }
        if (normalized.contains("python-requests")) {
            return "PYTHON_REQUESTS";
        }
        if (normalized.contains("mozilla")) {
            return "BROWSER";
        }
        return "UNKNOWN";
    }

    private Integer resolveBrowserTrusted(String userAgent, String browserFingerprint) {
        boolean browserLike = StringUtils.hasText(userAgent) && userAgent.toLowerCase().contains("mozilla");
        if (!browserLike) {
            return 0;
        }
        // 原判断要求指纹长度 >= 16，但前端旧版 hashString 输出最多 9 字符（fp_ + 6位base36），
        // 新版指纹已改用 SHA-256（fp_ + 64位十六进制 = 67字符），此处统一改为仅校验非空即可信任。
        return StringUtils.hasText(browserFingerprint) ? 1 : 0;
    }
}
