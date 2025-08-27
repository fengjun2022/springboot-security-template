package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API接口信息实体类
 * 用于存储系统中所有的API接口信息
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Data
public class ApiEndpointEntity {

    /** 主键ID */
    private Long id;

    /** 接口路径 */
    private String path;

    /** HTTP方法 (GET, POST, PUT, DELETE等) */
    private String method;

    /** 控制器类名 */
    private String controllerClass;

    /** 控制器方法名 */
    private String controllerMethod;

    /** 根路径 (类级别的@RequestMapping) */
    private String basePath;

    /** 接口描述 */
    private String description;

    /** 是否需要认证 (0-不需要, 1-需要) */
    private Integer requireAuth;

    /** 接口分组/模块 */
    private String moduleGroup;

    /** 是否启用 (0-禁用, 1-启用) */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 备注 */
    private String remark;
}
