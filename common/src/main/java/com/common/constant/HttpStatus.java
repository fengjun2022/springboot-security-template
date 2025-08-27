package com.common.constant;

public class HttpStatus {

    public static final int OK = 200; // 成功

    public static final int NO_KEYWORDS = 204; // 关键词未查询到资源
    public static final int BAD_REQUEST  = 400; // 失败

    public static final int NOT_LOGIN  = 401; // 失败


    public static final int FORBIDDEN   = 403; // 禁止

    public static final int NOT_FOUND    = 404; // 没有该资源

    public static final int NOT_USER    = 4001; // 没有该账户


    public static final int REQUEST_TIMEOUT    = 408;//请求超时

    public static final int SERVICE_UNAVAILABLE   = 500;//请求超时

    public static final int ES_ERROR   = 412; // ES错误




}
