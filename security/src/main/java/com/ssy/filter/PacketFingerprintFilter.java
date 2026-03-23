package com.ssy.filter;

import com.alibaba.fastjson.JSON;
import com.ssy.entity.Result;
import com.ssy.filter.support.CachedBodyHttpServletRequest;
import com.ssy.service.impl.PacketFingerprintService;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class PacketFingerprintFilter extends OncePerRequestFilter {

    private static final String HEADER_TIMESTAMP = "X-Packet-Timestamp";
    private static final String HEADER_NONCE = "X-Packet-Nonce";
    private static final String HEADER_SIGNATURE = "X-Packet-Signature";

    private final PacketFingerprintService packetFingerprintService;

    public PacketFingerprintFilter(PacketFingerprintService packetFingerprintService) {
        this.packetFingerprintService = packetFingerprintService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/login")
                || uri.startsWith("/security-auth")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.trim().isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest requestToUse = request;
        String body = "";
        if (shouldCacheBody(request)) {
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
            requestToUse = cachedRequest;
            body = cachedRequest.getCachedBodyAsString(8192);
        }

        String pathWithQuery = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            pathWithQuery += "?" + request.getQueryString();
        }

        PacketFingerprintService.VerifyResult verifyResult = packetFingerprintService.verify(
                authorization,
                request.getHeader(HEADER_TIMESTAMP),
                request.getHeader(HEADER_NONCE),
                request.getMethod(),
                pathWithQuery,
                body,
                request.getHeader(HEADER_SIGNATURE)
        );

        if (verifyResult.isSkip() || verifyResult.isPass()) {
            filterChain.doFilter(requestToUse, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(Result.error(verifyResult.getMessage(), HttpServletResponse.SC_BAD_REQUEST)));
    }

    private boolean shouldCacheBody(HttpServletRequest request) {
        String method = request.getMethod();
        return !"GET".equalsIgnoreCase(method) && !"DELETE".equalsIgnoreCase(method);
    }
}
