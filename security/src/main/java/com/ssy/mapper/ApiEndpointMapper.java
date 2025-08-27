package com.ssy.mapper;

import com.ssy.entity.ApiEndpointEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * API接口信息Mapper
 * 用于管理系统API接口的数据库操作
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
@Mapper
public interface ApiEndpointMapper {

        /**
         * 插入API接口信息
         * 
         * @param apiEndpoint API接口实体
         * @return 影响行数
         */
        @Insert("INSERT INTO api_endpoints (path, method, controller_class, controller_method, " +
                        "base_path, description, require_auth, module_group, status, remark) " +
                        "VALUES (#{path}, #{method}, #{controllerClass}, #{controllerMethod}, " +
                        "#{basePath}, #{description}, #{requireAuth}, #{moduleGroup}, #{status}, #{remark})")
        @Options(useGeneratedKeys = true, keyProperty = "id")
        int insert(ApiEndpointEntity apiEndpoint);

        /**
         * 批量插入API接口信息
         * 
         * @param apiEndpoints API接口列表
         * @return 影响行数
         */
        @Insert("<script>" +
                        "INSERT INTO api_endpoints (path, method, controller_class, controller_method, " +
                        "base_path, description, require_auth, module_group, status, remark) VALUES " +
                        "<foreach collection='list' item='item' separator=','>" +
                        "(#{item.path}, #{item.method}, #{item.controllerClass}, #{item.controllerMethod}, " +
                        "#{item.basePath}, #{item.description}, #{item.requireAuth}, #{item.moduleGroup}, " +
                        "#{item.status}, #{item.remark})" +
                        "</foreach>" +
                        "</script>")
        int batchInsert(@Param("list") List<ApiEndpointEntity> apiEndpoints);

        /**
         * 根据路径和方法查询API接口
         * 
         * @param path   接口路径
         * @param method HTTP方法
         * @return API接口实体
         */
        @Select("SELECT * FROM api_endpoints WHERE path = #{path} AND method = #{method}")
        ApiEndpointEntity selectByPathAndMethod(@Param("path") String path, @Param("method") String method);

        /**
         * 查询所有API接口
         * 
         * @return API接口列表
         */
        @Select("SELECT * FROM api_endpoints ORDER BY controller_class, path")
        List<ApiEndpointEntity> selectAll();

        /**
         * 分页查询API接口
         * 
         * @param offset      偏移量
         * @param limit       限制数量
         * @param keyword     搜索关键词
         * @param moduleGroup 模块分组
         * @return API接口列表
         */
        @Select("<script>" +
                        "SELECT * FROM api_endpoints WHERE 1=1 " +
                        "<if test='keyword != null and keyword != \"\"'>" +
                        "AND (path LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR description LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR controller_class LIKE CONCAT('%', #{keyword}, '%')) " +
                        "</if>" +
                        "<if test='moduleGroup != null and moduleGroup != \"\"'>" +
                        "AND module_group = #{moduleGroup} " +
                        "</if>" +
                        "ORDER BY controller_class, path " +
                        "LIMIT #{offset}, #{limit}" +
                        "</script>")
        List<ApiEndpointEntity> selectByPage(@Param("offset") int offset,
                        @Param("limit") int limit,
                        @Param("keyword") String keyword,
                        @Param("moduleGroup") String moduleGroup);

        /**
         * 统计API接口总数
         * 
         * @param keyword     搜索关键词
         * @param moduleGroup 模块分组
         * @return 总数
         */
        @Select("<script>" +
                        "SELECT COUNT(*) FROM api_endpoints WHERE 1=1 " +
                        "<if test='keyword != null and keyword != \"\"'>" +
                        "AND (path LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR description LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR controller_class LIKE CONCAT('%', #{keyword}, '%')) " +
                        "</if>" +
                        "<if test='moduleGroup != null and moduleGroup != \"\"'>" +
                        "AND module_group = #{moduleGroup} " +
                        "</if>" +
                        "</script>")
        int countByCondition(@Param("keyword") String keyword, @Param("moduleGroup") String moduleGroup);

        /**
         * 更新API接口信息
         * 
         * @param apiEndpoint API接口实体
         * @return 影响行数
         */
        @Update("UPDATE api_endpoints SET " +
                        "description = #{description}, require_auth = #{requireAuth}, " +
                        "module_group = #{moduleGroup}, status = #{status}, remark = #{remark} " +
                        "WHERE id = #{id}")
        int update(ApiEndpointEntity apiEndpoint);

        /**
         * 删除所有API接口
         * 
         * @return 影响行数
         */
        @Delete("DELETE FROM api_endpoints")
        int deleteAll();

        /**
         * 根据控制器类名删除API接口
         * 
         * @param controllerClass 控制器类名
         * @return 影响行数
         */
        @Delete("DELETE FROM api_endpoints WHERE controller_class = #{controllerClass}")
        int deleteByControllerClass(@Param("controllerClass") String controllerClass);

        /**
         * 查询所有模块分组
         * 
         * @return 模块分组列表
         */
        @Select("SELECT DISTINCT module_group FROM api_endpoints WHERE module_group IS NOT NULL ORDER BY module_group")
        List<String> selectAllModuleGroups();

        /**
         * 根据控制器类名查询API接口
         * 
         * @param controllerClass 控制器类名
         * @return API接口列表
         */
        @Select("SELECT * FROM api_endpoints WHERE controller_class = #{controllerClass}")
        List<ApiEndpointEntity> selectByControllerClass(@Param("controllerClass") String controllerClass);
}
