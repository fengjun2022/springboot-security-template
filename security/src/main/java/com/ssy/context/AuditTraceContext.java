package com.ssy.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuditTraceContext {
    private String traceId;
    private String requestMethod;
    private String requestUri;
    private String clientIp;
    private Long endpointId;
    private String moduleGroup;
    private String description;
    private List<String> permissionCodes = new ArrayList<>();
    private String requestBodySample;
    private String responseBodySample;
}
