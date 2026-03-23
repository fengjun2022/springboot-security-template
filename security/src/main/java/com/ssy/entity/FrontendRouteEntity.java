package com.ssy.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class FrontendRouteEntity {
    private Long id;
    private Long parentId;
    private String routeName;
    private String routePath;
    private String component;
    private String redirectPath;
    private String title;
    private String icon;
    /**
     * DIRECTORY / MENU / BUTTON
     */
    private String resourceType;
    private String permissionCode;
    private Integer status;
    /**
     * 1-显示在菜单中，0-隐藏
     */
    private Integer visible;
    private Integer sort;
    private Integer keepAlive;
    private Integer alwaysShow;
    private Integer ignoreAuth;
    private String activeMenu;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<FrontendRouteEntity> children = new ArrayList<>();
}
