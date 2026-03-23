package com.ssy.mapper;

import com.ssy.entity.SecurityIpBlacklistEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SecurityIpBlacklistMapper {

    @Select("SELECT * FROM security_ip_blacklist " +
            "WHERE status = 1 AND (expire_time IS NULL OR expire_time > NOW())")
    List<SecurityIpBlacklistEntity> selectActiveList();

    @Select("SELECT * FROM security_ip_blacklist ORDER BY id DESC LIMIT #{limit}")
    List<SecurityIpBlacklistEntity> selectRecent(@Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM security_ip_blacklist WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'> AND (ip LIKE CONCAT('%', #{keyword}, '%') OR reason LIKE CONCAT('%', #{keyword}, '%'))</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</script>")
    int countByCondition(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select("<script>" +
            "SELECT * FROM security_ip_blacklist WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'> AND (ip LIKE CONCAT('%', #{keyword}, '%') OR reason LIKE CONCAT('%', #{keyword}, '%'))</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            " ORDER BY id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<SecurityIpBlacklistEntity> selectByPage(@Param("offset") int offset,
                                                 @Param("size") int size,
                                                 @Param("keyword") String keyword,
                                                 @Param("status") Integer status);

    @Insert("INSERT INTO security_ip_blacklist " +
            "(ip, status, source, reason, attack_type, hit_count, first_hit_time, last_hit_time, expire_time, create_time, update_time, remark) " +
            "VALUES (#{ip}, 1, #{source}, #{reason}, #{attackType}, #{hitCount}, #{firstHitTime}, #{lastHitTime}, #{expireTime}, #{createTime}, #{updateTime}, #{remark}) " +
            "ON DUPLICATE KEY UPDATE " +
            "status = 1, " +
            "source = VALUES(source), " +
            "reason = VALUES(reason), " +
            "attack_type = VALUES(attack_type), " +
            "hit_count = IFNULL(hit_count, 0) + VALUES(hit_count), " +
            "last_hit_time = VALUES(last_hit_time), " +
            "expire_time = VALUES(expire_time), " +
            "update_time = VALUES(update_time)")
    int upsert(SecurityIpBlacklistEntity entity);

    @Update("UPDATE security_ip_blacklist SET status = 0, update_time = #{now} WHERE expire_time IS NOT NULL AND expire_time <= #{now} AND status = 1")
    int disableExpired(LocalDateTime now);

    @Update("UPDATE security_ip_blacklist SET status = 0, update_time = NOW() WHERE ip = #{ip}")
    int disableByIp(@Param("ip") String ip);
}
