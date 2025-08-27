package com.ssy.dto;

import lombok.Data;

import java.util.List;

/**
 * 服务应用更新请求DTO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Data
public class ServiceAppUpdateDTO {

    /**
     * 应用ID
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 允许访问的接口列表
     */
    private List<String> allowedApis;

    /**
     * 更新者
     */
    private String updateBy;

    /**
     * 备注
     */
    private String remark;
}
