package com.ssy.dto;

import lombok.Data;

/**
 * 服务Token签发请求DTO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Data
public class ServiceTokenIssueDTO {

    /**
     * 应用ID
     */
    private String appId;

    /**
     * 授权码
     */
    private String authCode;

    /**
     * 签发者
     */
    private String issueBy;
}
