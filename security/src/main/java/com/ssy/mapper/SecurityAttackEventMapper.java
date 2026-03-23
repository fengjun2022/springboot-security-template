package com.ssy.mapper;

import com.ssy.entity.SecurityAttackEventEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SecurityAttackEventMapper {

    @Insert("INSERT INTO security_attack_event " +
            "(ip, country, region_name, city, isp, location_label, attack_type, path, method, endpoint_id, username, app_id, client_tool, " +
            "browser_fingerprint, browser_trusted, user_agent, referer, query_string, request_body_sample, request_body_hash, " +
            "risk_score, block_action, block_reason, suggested_action, create_time) " +
            "VALUES (#{ip}, #{country}, #{regionName}, #{city}, #{isp}, #{locationLabel}, #{attackType}, #{path}, #{method}, #{endpointId}, #{username}, #{appId}, #{clientTool}, " +
            "#{browserFingerprint}, #{browserTrusted}, #{userAgent}, #{referer}, #{queryString}, #{requestBodySample}, #{requestBodyHash}, " +
            "#{riskScore}, #{blockAction}, #{blockReason}, #{suggestedAction}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SecurityAttackEventEntity entity);

    @Update("UPDATE security_attack_event SET country = #{country}, region_name = #{regionName}, city = #{city}, isp = #{isp}, " +
            "location_label = #{locationLabel} WHERE id = #{id}")
    int updateGeoInfo(SecurityAttackEventEntity entity);

    @Select("SELECT * FROM security_attack_event ORDER BY id DESC LIMIT #{limit}")
    List<SecurityAttackEventEntity> selectRecent(@Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM security_attack_event WHERE 1=1 " +
            "<if test='ip != null and ip != \"\"'>AND ip = #{ip} </if>" +
            "<if test='attackType != null and attackType != \"\"'>AND attack_type = #{attackType} </if>" +
            "<if test='startTime != null'>AND create_time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND create_time &lt;= #{endTime} </if>" +
            "</script>")
    int countByCondition(@Param("ip") String ip,
                         @Param("attackType") String attackType,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);

    @Select("<script>" +
            "SELECT * FROM security_attack_event WHERE 1=1 " +
            "<if test='ip != null and ip != \"\"'>AND ip = #{ip} </if>" +
            "<if test='attackType != null and attackType != \"\"'>AND attack_type = #{attackType} </if>" +
            "<if test='startTime != null'>AND create_time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND create_time &lt;= #{endTime} </if>" +
            "ORDER BY id DESC LIMIT #{offset}, #{limit}" +
            "</script>")
    List<SecurityAttackEventEntity> selectByPage(@Param("offset") int offset,
                                                 @Param("limit") int limit,
                                                 @Param("ip") String ip,
                                                 @Param("attackType") String attackType,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);
}
