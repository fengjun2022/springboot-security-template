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
public class UserEntity {
     private long id;
     private String username;
     private String password;
     private String email;
     private String phone;
     private int status;
     private String token;
     private Collection<String> authorities;

     // 兼容方法
     public void setId(long id) {
          this.id = id;
     }

     public void setUsername(String username) {
          this.username = username;
     }

     public void setStatus(int status) {
          this.status = status;
     }

     public void setPassword(String password) {
          this.password = password;
     }

     public void setToken(String token) {
          this.token = token;
     }

     public void setAuthorities(Collection<String> authorities) {
          this.authorities = authorities;
     }

     public String getPassword() {
          return this.password;
     }

     public String getUsername() {
          return this.username;
     }

     public Collection<String> getAuthorities() {
          return this.authorities;
     }

     public int getStatus() {
          return this.status;
     }
}