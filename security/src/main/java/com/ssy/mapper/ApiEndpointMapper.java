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
                "base_path, description, auth, require_auth, module_group, status, remark, " +
                "create_time, update_time) " +
                "VALUES (#{path}, #{method}, #{controllerClass}, #{controllerMethod}, " +
                "#{basePath}, #{description}, #{auth}, #{requireAuth}, #{moduleGroup}, " +
                "#{status}, #{remark}, #{createTime}, #{updateTime})")
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
                "base_path, description, auth, require_auth, module_group, status, remark, " +
                "create_time, update_time) VALUES " +
                "<foreach collection='list' item='item' separator=','>" +
                "(#{item.path}, #{item.method}, #{item.controllerClass}, #{item.controllerMethod}, " +
                "#{item.basePath}, #{item.description}, #{item.auth}, #{item.requireAuth}, " +
                "#{item.moduleGroup}, #{item.status}, #{item.remark}, #{item.createTime}, #{item.updateTime})" +
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
                "OR controller_class LIKE CONCAT('%', #{keyword}, '%') " +
                "OR auth LIKE CONCAT('%', #{keyword}, '%')) " +
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
                "OR controller_class LIKE CONCAT('%', #{keyword}, '%') " +
                "OR auth LIKE CONCAT('%', #{keyword}, '%')) " +
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
                "controller_class = #{controllerClass}, controller_method = #{controllerMethod}, " +
                "base_path = #{basePath}, description = #{description}, auth = #{auth}, " +
                "require_auth = #{requireAuth}, module_group = #{moduleGroup}, " +
                "status = #{status}, remark = #{remark}, update_time = #{updateTime} " +
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

        /**
         * 根据权限表达式查询API接口
         *
         * @param authExpression 权限表达式关键词
         * @return API接口列表
         */
        @Select("SELECT * FROM api_endpoints WHERE auth LIKE CONCAT('%', #{authExpression}, '%') ORDER BY path")
        List<ApiEndpointEntity> selectByAuthExpression(@Param("authExpression") String authExpression);

        /**
         * 查询需要特定角色权限的API接口
         *
         * @param role 角色名
         * @return API接口列表
         */
        @Select("SELECT * FROM api_endpoints WHERE auth LIKE CONCAT('%', #{role}, '%') ORDER BY controller_class, path")
        List<ApiEndpointEntity> selectByRole(@Param("role") String role);

        /**
         * 查询无权限要求的API接口
         *
         * @return API接口列表
         */
        @Select("SELECT * FROM api_endpoints WHERE require_auth = 0 OR auth IS NULL OR auth = '' ORDER BY path")
        List<ApiEndpointEntity> selectPublicEndpoints();

        /**
         * 查询有权限要求的API接口
         *
         * @return API接口列表
         */
        @Select("SELECT * FROM api_endpoints WHERE require_auth = 1 AND auth IS NOT NULL AND auth != '' ORDER BY path")
        List<ApiEndpointEntity> selectProtectedEndpoints();

        /**
         * 根据权限类型统计接口数量
         *
         * @return 统计结果映射
         */
        @Select("SELECT " +
                "SUM(CASE WHEN require_auth = 0 OR auth IS NULL OR auth = '' THEN 1 ELSE 0 END) as publicCount, " +
                "SUM(CASE WHEN require_auth = 1 AND auth IS NOT NULL AND auth != '' THEN 1 ELSE 0 END) as protectedCount, " +
                "SUM(CASE WHEN auth LIKE '%PreAuthorize%' THEN 1 ELSE 0 END) as preAuthorizeCount, " +
                "SUM(CASE WHEN auth LIKE '%Secured%' THEN 1 ELSE 0 END) as securedCount, " +
                "SUM(CASE WHEN auth LIKE '%RolesAllowed%' THEN 1 ELSE 0 END) as rolesAllowedCount, " +
                "COUNT(*) as totalCount " +
                "FROM api_endpoints")
        @Results({
                @Result(column = "publicCount", property = "publicCount"),
                @Result(column = "protectedCount", property = "protectedCount"),
                @Result(column = "preAuthorizeCount", property = "preAuthorizeCount"),
                @Result(column = "securedCount", property = "securedCount"),
                @Result(column = "rolesAllowedCount", property = "rolesAllowedCount"),
                @Result(column = "totalCount", property = "totalCount")
        })
        AuthStatistics getAuthStatistics();

        /**
         * 权限统计结果类
         */
        class AuthStatistics {
                private int publicCount;
                private int protectedCount;
                private int preAuthorizeCount;
                private int securedCount;
                private int rolesAllowedCount;
                private int totalCount;

                // Getters and Setters
                public int getPublicCount() { return publicCount; }
                public void setPublicCount(int publicCount) { this.publicCount = publicCount; }
                public int getProtectedCount() { return protectedCount; }
                public void setProtectedCount(int protectedCount) { this.protectedCount = protectedCount; }
                public int getPreAuthorizeCount() { return preAuthorizeCount; }
                public void setPreAuthorizeCount(int preAuthorizeCount) { this.preAuthorizeCount = preAuthorizeCount; }
                public int getSecuredCount() { return securedCount; }
                public void setSecuredCount(int securedCount) { this.securedCount = securedCount; }
                public int getRolesAllowedCount() { return rolesAllowedCount; }
                public void setRolesAllowedCount(int rolesAllowedCount) { this.rolesAllowedCount = rolesAllowedCount; }
                public int getTotalCount() { return totalCount; }
                public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        }

        /**
         * 批量更新接口权限信息
         *
         * @param apiEndpoints API接口列表
         * @return 影响行数
         */
        @Update("<script>" +
                "<foreach collection='list' item='item' separator=';'>" +
                "UPDATE api_endpoints SET " +
                "auth = #{item.auth}, require_auth = #{item.requireAuth}, update_time = #{item.updateTime} " +
                "WHERE path = #{item.path} AND method = #{item.method}" +
                "</foreach>" +
                "</script>")
        int batchUpdateAuth(@Param("list") List<ApiEndpointEntity> apiEndpoints);
}