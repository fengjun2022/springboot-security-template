package com.ssy.filter.support;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

/**
 * 捕获最终响应状态码，用于过滤器链后置分析（如401/403回流埋点）。
 */
public class StatusCaptureHttpServletResponse extends HttpServletResponseWrapper {

    private int status = HttpServletResponse.SC_OK;

    public StatusCaptureHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
        super.setStatus(sc);
    }

    @Override
    public void sendError(int sc) throws IOException {
        this.status = sc;
        super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        this.status = sc;
        super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        this.status = HttpServletResponse.SC_FOUND;
        super.sendRedirect(location);
    }

    @Override
    public int getStatus() {
        return status;
    }
}
