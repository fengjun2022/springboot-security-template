package com.ssy.constant;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/3
 * @email 3278440884@qq.com
 */


public class LoginRequest {
    private String username;
    private String password;
    private String captchaToken;
    private String captchaCode;
    private String browserFingerprint;

    // getter 和 setter 方法
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCaptchaToken() {
        return captchaToken;
    }

    public void setCaptchaToken(String captchaToken) {
        this.captchaToken = captchaToken;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }

    public String getBrowserFingerprint() {
        return browserFingerprint;
    }

    public void setBrowserFingerprint(String browserFingerprint) {
        this.browserFingerprint = browserFingerprint;
    }

}
