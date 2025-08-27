package com.ssy.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 服务Token实体类
 * 用于存储签发的永久token信息
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceTokenEntity {

    /**
     * 主键ID，自增
     */
    private Long id;

    /**
     * 应用ID，关联service_app表
     */
    private String appId;

    /**
     * 签发的token
     */
    private String token;

    /**
     * token类型，固定为"permanent"表示永久token
     */
    private String tokenType;

    /**
     * token状态：1-有效，0-失效
     */
    private Integer status;

    /**
     * 是否有效：1-有效，0-失效
     */
    private Integer isValid;

    /**
     * 签发时间
     */
    private LocalDateTime issueTime;

    /**
     * 最后使用时间
     */
    private LocalDateTime lastUsedTime;

    /**
     * 签发者
     */
    private String issueBy;

    /**
     * 备注
     */
    private String remark;
}
