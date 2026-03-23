package com.ssy.holder;

import com.ssy.context.AuditTraceContext;

public final class AuditTraceContextHolder {

    private static final ThreadLocal<AuditTraceContext> LOCAL = new ThreadLocal<>();

    private AuditTraceContextHolder() {
    }

    public static void set(AuditTraceContext context) {
        if (context == null) {
            LOCAL.remove();
            return;
        }
        LOCAL.set(context);
    }

    public static AuditTraceContext get() {
        return LOCAL.get();
    }

    public static void clear() {
        LOCAL.remove();
    }
}
