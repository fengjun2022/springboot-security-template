package com.ssy.dto;

import lombok.Data;

import java.util.List;

/**
 * 服务应用注册请求DTO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Data
public class ServiceAppRegisterDTO {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 允许访问的接口列表
     */
    private List<String> allowedApis;

    /**
     * 创建者
     */
    private String createBy;

    /**
     * 备注
     */
    private String remark;
}
