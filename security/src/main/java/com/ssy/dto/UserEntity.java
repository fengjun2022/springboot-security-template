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
     private Long id;
     private String username;
     private String password;
     private String email;
     private String phone;
     private Integer status;
     private String token;
     private Long userId;
     private Collection<String> authorities;
}