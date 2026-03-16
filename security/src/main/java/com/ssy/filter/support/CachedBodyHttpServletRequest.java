package com.ssy.filter.support;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 仅用于小请求体缓存，支持后续过滤器/控制器继续读取。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = readAllBytes(request);
    }

    public String getCachedBodyAsString(int maxLen) {
        if (cachedBody == null || cachedBody.length == 0 || maxLen <= 0) {
            return null;
        }
        int len = Math.min(maxLen, cachedBody.length);
        Charset charset = resolveCharset(getRequest());
        return new String(cachedBody, 0, len, charset);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() <= 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 同步读取场景，无需实现
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), resolveCharset(getRequest())));
    }

    private static Charset resolveCharset(ServletRequest request) {
        try {
            String encoding = request.getCharacterEncoding();
            if (encoding != null && !encoding.isEmpty()) {
                return Charset.forName(encoding);
            }
        } catch (Exception ignored) {
            // fallback UTF-8
        }
        return StandardCharsets.UTF_8;
    }

    private static byte[] readAllBytes(HttpServletRequest request) throws IOException {
        byte[] buffer = new byte[1024];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = request.getInputStream().read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
