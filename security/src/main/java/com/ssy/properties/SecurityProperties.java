package com.ssy.properties;

import com.ssy.factory.YamlPropertySourceFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 *
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/4
 * @email 3278440884@qq.com
 */

@Data
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // 最高优先级
@PropertySource(value = "classpath:security.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "security")

public class SecurityProperties {

    private List<String> permitAll;
    private List<RoleMapping> roleBased;
    private EndpointRbac endpointRbac = new EndpointRbac();

    @PostConstruct
    public void init() {
        System.err.println("SecurityProperties 初始化完成");
        System.err.println("permitAll: {}"+ this.permitAll);
        System.err.println("roleBased: {}"+ roleBased);
    }
    public List<String> getPermitAll() {
        return permitAll;
    }

    public void setPermitAll(List<String> permitAll) {
        this.permitAll = permitAll;
    }

    public List<RoleMapping> getRoleBased() {
        return roleBased;
    }

    public void setRoleBased(List<RoleMapping> roleBased) {
        this.roleBased = roleBased;
    }

    public static class RoleMapping {
        private String pattern;
        private String role; // 例如：ADMIN

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    @Data
    public static class EndpointRbac {
        /**
         * 严格模式：当接口已扫描且 require_auth=1 但未绑定任何权限时，直接拒绝访问。
         * 默认 false，便于平滑迁移。
         */
        private Boolean strictUnboundPermissionDeny = false;
    }
}
