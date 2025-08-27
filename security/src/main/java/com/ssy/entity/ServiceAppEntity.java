package com.ssy.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务应用注册实体类
 * 用于管理服务间调用的应用信息和权限
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceAppEntity {

    /**
     * 主键ID，自增
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用唯一标识，使用雪花ID生成
     */
    private String appId;

    /**
     * 授权码，自动生成的密钥
     */
    private String authCode;

    /**
     * 允许访问的接口列表，JSON格式存储
     */
    private String allowedApis;

    /**
     * 应用状态：1-启用，0-禁用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建者
     */
    private String createBy;

    /**
     * 更新者
     */
    private String updateBy;

    /**
     * 备注
     */
    private String remark;

    /**
     * 允许访问的接口列表（用于业务逻辑，不存储到数据库）
     */
    private List<String> allowedApiList;

    /**
     * 获取允许访问的接口列表 - 兼容性方法
     */
    public String getAllowedApis() {
        return this.allowedApis;
    }

    /**
     * 设置允许访问的接口列表 - 兼容性方法
     */
    public void setAllowedApis(String allowedApis) {
        this.allowedApis = allowedApis;
    }
}
