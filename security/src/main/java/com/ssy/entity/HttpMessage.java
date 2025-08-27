package com.ssy.entity;

public class HttpMessage {
    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String NO_TOKEN = "用户未登录，请登录后尝试";

    public static final String LOGIN_FAILURE= "登录失败";

    public static final String LOGIN_USER_PASSWORD_ERROR= "密码错误";

    public static final String LOGIN_USER_USERNAME_ERROR= "用户不存在";

    public static final String AUTHENTICATION_FAILED= "认证失败";

    public static final String TOKEN_EXPIRED= "登录超时";
    public static final String TOKEN_INVALID= "Token错误，解析失败";
    public static final String BAD_REQUEST= "Token验证异常: ";
    public static final String  TOKEN_ALGORITHM_MISMATCH= "Token算法异常";




}
