package com.ssy.service;

import com.ssy.entity.ServiceTokenEntity;

import java.util.List;

/**
 * 服务TokenService接口
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
public interface ServiceTokenService {

    /**
     * 签发永久token
     * 
     * @param appId    应用ID
     * @param authCode 授权码
     * @param issueBy  签发者
     * @return 签发的token信息
     */
    ServiceTokenEntity issueToken(String appId, String authCode, String issueBy);

    /**
     * 验证token
     * 
     * @param token token值
     * @return 验证成功的token信息，失败返回null
     */
    ServiceTokenEntity validateToken(String token);

    /**
     * 根据appId获取有效token
     * 
     * @param appId 应用ID
     * @return token信息
     */
    ServiceTokenEntity getTokenByAppId(String appId);

    /**
     * 更新token最后使用时间
     * 
     * @param token token值
     */
    void updateLastUsedTime(String token);

    /**
     * 失效token
     * 
     * @param tokenId token主键ID
     */
    void invalidateToken(Long tokenId);

    /**
     * 根据appId失效所有token
     * 
     * @param appId 应用ID
     */
    void invalidateTokensByAppId(String appId);

    /**
     * 重新生成token
     * 
     * @param appId    应用ID
     * @param authCode 授权码
     * @param issueBy  签发者
     * @return 新的token信息
     */
    ServiceTokenEntity regenerateToken(String appId, String authCode, String issueBy);

    /**
     * 获取所有Token
     * 
     * @return Token列表
     */
    List<ServiceTokenEntity> getAllTokens();
}
