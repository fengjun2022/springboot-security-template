package conm.test;

import com.main.MainApplication;
import com.ssy.properties.JwtProperties;
import com.ssy.properties.SecurityProperties;
import com.ssy.repository.UserRepository;
import com.ssy.utils.SecurePasswordEncryptorUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import java.util.concurrent.CompletableFuture;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2025/3/5
 * @email 3278440884@qq.com
 */


@SpringBootTest(classes = {MainApplication.class})
public class test {
    @Autowired
    SecurePasswordEncryptorUtil securePasswordEncryptorUtil;
    @Autowired
    JwtProperties jwtProperties;
    @Autowired
    SecurityProperties  securityProperties;
    @Test
    public void testPassword(){
        System.out.println(jwtProperties.getHeadName());
    }
}
