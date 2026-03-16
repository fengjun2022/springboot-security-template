package com.ssy.dto;

import lombok.Data;
import java.util.Collection;

/**
 * 用户实体DTO（兼容旧代码）
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Data
public class UserEntity extends com.pojo.entity.UserEntity {
     private long id;
     private String username;
     private String password;
     private String email;
     private String phone;
     private Integer status;
     private String token;
     private String loginType;
     private Long userId;
     /**
      * 兼容旧字段：不再作为标准RBAC主数据来源
      */
     private Collection<String> authorities;
     /**
      * 标准RBAC角色编码集合（如 ADMIN / USER）
      */
     private Collection<String> roles;
     /**
      * 标准RBAC权限编码集合（如 iam:user:create）
      */
     private Collection<String> permissions;
}
