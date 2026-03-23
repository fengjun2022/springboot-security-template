package com.ssy.mapper;

import com.ssy.entity.FrontendRouteEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FrontendRouteMapper {

    @Select("SELECT * FROM sys_frontend_route ORDER BY sort ASC, id ASC")
    List<FrontendRouteEntity> selectAll();

    @Select("SELECT * FROM sys_frontend_route WHERE status = 1 ORDER BY sort ASC, id ASC")
    List<FrontendRouteEntity> selectEnabledList();

    @Select("SELECT * FROM sys_frontend_route WHERE id = #{id}")
    FrontendRouteEntity selectById(@Param("id") Long id);

    @Select("SELECT * FROM sys_frontend_route WHERE route_name = #{routeName}")
    FrontendRouteEntity selectByRouteName(@Param("routeName") String routeName);

    @Insert("INSERT INTO sys_frontend_route (" +
            "parent_id, route_name, route_path, component, redirect_path, title, icon, resource_type, " +
            "permission_code, status, visible, sort, keep_alive, always_show, ignore_auth, active_menu, remark, create_time, update_time" +
            ") VALUES (" +
            "#{parentId}, #{routeName}, #{routePath}, #{component}, #{redirectPath}, #{title}, #{icon}, #{resourceType}, " +
            "#{permissionCode}, #{status}, #{visible}, #{sort}, #{keepAlive}, #{alwaysShow}, #{ignoreAuth}, #{activeMenu}, #{remark}, #{createTime}, #{updateTime}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FrontendRouteEntity entity);

    @Update("<script>" +
            "UPDATE sys_frontend_route <set>" +
            "parent_id = #{parentId}," +
            "route_name = #{routeName}," +
            "route_path = #{routePath}," +
            "component = #{component}," +
            "redirect_path = #{redirectPath}," +
            "title = #{title}," +
            "icon = #{icon}," +
            "resource_type = #{resourceType}," +
            "permission_code = #{permissionCode}," +
            "status = #{status}," +
            "visible = #{visible}," +
            "sort = #{sort}," +
            "keep_alive = #{keepAlive}," +
            "always_show = #{alwaysShow}," +
            "ignore_auth = #{ignoreAuth}," +
            "active_menu = #{activeMenu}," +
            "remark = #{remark}," +
            "update_time = #{updateTime}" +
            "</set> WHERE id = #{id}" +
            "</script>")
    int update(FrontendRouteEntity entity);
}
