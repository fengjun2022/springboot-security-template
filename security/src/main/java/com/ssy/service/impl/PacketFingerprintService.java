package com.ssy.service.impl;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PacketFingerprintService {

    private static final long MAX_SKEW_MILLIS = 60_000L;
    private static final long NONCE_EXPIRE_MILLIS = 60_000L;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SessionSecret> sessionSecretMap = new ConcurrentHashMap<>();
    private final Map<String, Long> nonceCache = new ConcurrentHashMap<>();

    public String issueSecret(String token, long tokenExpireAt) {
        token = normalizeToken(token);
        String secret = randomSecret();
        sessionSecretMap.put(token, new SessionSecret(secret, tokenExpireAt));
        return secret;
    }

    public VerifyResult verify(String token,
                               String timestamp,
                               String nonce,
                               String method,
                               String pathWithQuery,
                               String body,
                               String signature) {
        cleanupExpired();
        token = normalizeToken(token);
        if (!StringUtils.hasText(token)) {
            return VerifyResult.skip();
        }
        SessionSecret sessionSecret = sessionSecretMap.get(token);
        if (sessionSecret == null) {
            return VerifyResult.fail("请求会话指纹不存在");
        }
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            return VerifyResult.fail("请求包指纹缺失");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException ex) {
            return VerifyResult.fail("请求包时间戳非法");
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - ts) > MAX_SKEW_MILLIS) {
            return VerifyResult.fail("请求包已过期");
        }

        String nonceKey = token + "#" + nonce.trim();
        Long existing = nonceCache.putIfAbsent(nonceKey, now + NONCE_EXPIRE_MILLIS);
        if (existing != null && existing > now) {
            return VerifyResult.fail("请求包疑似重放");
        }

        String expected = fingerprint(sessionSecret.secret, timestamp.trim(), nonce.trim(), method, pathWithQuery, body);
        if (!expected.equals(signature.trim())) {
            nonceCache.remove(nonceKey);
            return VerifyResult.fail("请求包指纹校验失败");
        }
        return VerifyResult.pass();
    }

    public String fingerprint(String secret,
                              String timestamp,
                              String nonce,
                              String method,
                              String pathWithQuery,
                              String body) {
        String raw = safe(secret) + "|" + safe(timestamp) + "|" + safe(nonce) + "|" + safe(method).toUpperCase()
                + "|" + canonicalizePathWithQuery(pathWithQuery) + "|" + canonicalizeBody(body);
        return fnv1a64(raw);
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        sessionSecretMap.entrySet().removeIf(entry -> entry.getValue().expireAt > 0 && entry.getValue().expireAt < now);
        nonceCache.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private String randomSecret() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String fnv1a64(String text) {
        long hash = 0xcbf29ce484222325L;
        long prime = 0x100000001b3L;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= prime;
        }
        return Long.toUnsignedString(hash, 16);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return token;
        }
        String normalized = token.trim();
        if (normalized.startsWith("Bearer ")) {
            return normalized.substring(7).trim();
        }
        return normalized;
    }

    private String canonicalizePathWithQuery(String pathWithQuery) {
        if (!StringUtils.hasText(pathWithQuery)) {
            return "";
        }
        int questionIndex = pathWithQuery.indexOf('?');
        if (questionIndex < 0) {
            return pathWithQuery;
        }
        String path = pathWithQuery.substring(0, questionIndex);
        String queryString = pathWithQuery.substring(questionIndex + 1);
        if (!StringUtils.hasText(queryString)) {
            return path;
        }
        String[] parts = queryString.split("&");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                normalized.add(part.trim());
            }
        }
        Collections.sort(normalized);
        return path + "?" + String.join("&", normalized);
    }

    private String canonicalizeBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            Object parsed = JSON.parse(body);
            return stableStringify(parsed);
        } catch (Exception ignored) {
            return body.trim();
        }
    }

    @SuppressWarnings("unchecked")
    private String stableStringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            List<String> items = new ArrayList<>();
            for (Object item : list) {
                items.add(stableStringify(item));
            }
            return "[" + String.join(",", items) + "]";
        }
        if (value instanceof Map) {
            Map<String, Object> source = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> source.put(String.valueOf(key), item));
            List<String> keys = new ArrayList<>(source.keySet());
            Collections.sort(keys);
            List<String> entries = new ArrayList<>();
            for (String key : keys) {
                entries.add(key + ":" + stableStringify(source.get(key)));
            }
            return "{" + String.join(",", entries) + "}";
        }
        return String.valueOf(value);
    }

    private static class SessionSecret {
        private final String secret;
        private final long expireAt;

        private SessionSecret(String secret, long expireAt) {
            this.secret = secret;
            this.expireAt = expireAt;
        }
    }

    public static class VerifyResult {
        private final boolean pass;
        private final boolean skip;
        private final String message;

        private VerifyResult(boolean pass, boolean skip, String message) {
            this.pass = pass;
            this.skip = skip;
            this.message = message;
        }

        public static VerifyResult pass() {
            return new VerifyResult(true, false, null);
        }

        public static VerifyResult fail(String message) {
            return new VerifyResult(false, false, message);
        }

        public static VerifyResult skip() {
            return new VerifyResult(false, true, null);
        }

        public boolean isPass() {
            return pass;
        }

        public boolean isSkip() {
            return skip;
        }

        public String getMessage() {
            return message;
        }
    }
}
