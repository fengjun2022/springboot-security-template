package com.ssy.mapper;

import com.ssy.entity.SecurityIpWhitelistEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SecurityIpWhitelistMapper {

    @Select("SELECT * FROM security_ip_whitelist WHERE status = 1")
    List<SecurityIpWhitelistEntity> selectEnabledList();

    @Select("SELECT * FROM security_ip_whitelist ORDER BY id DESC LIMIT #{limit}")
    List<SecurityIpWhitelistEntity> selectRecent(@Param("limit") int limit);

    @Insert("INSERT INTO security_ip_whitelist (ip_or_cidr, status, remark, create_time, update_time) " +
            "VALUES (#{ipOrCidr}, 1, #{remark}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE status = 1, remark = VALUES(remark), update_time = NOW()")
    int upsert(@Param("ipOrCidr") String ipOrCidr, @Param("remark") String remark);

    @Update("UPDATE security_ip_whitelist SET status = 0, update_time = NOW() WHERE ip_or_cidr = #{ipOrCidr}")
    int disableByIpOrCidr(@Param("ipOrCidr") String ipOrCidr);
}
