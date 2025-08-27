package com.ssy.utils;

/**
 * TODO
 *
 * @author Mr.fengjun
 * @version 1.0
 * @date 2024/12/5
 * @email 3278440884@qq.com
 */

import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;
import org.springframework.stereotype.Component;

/**
 * 封装基于 Yitter 的雪花 ID 生成器。
 */
@Component
public class IdGenerator {
//    分布式项目则从配置文件中读取
    private static final short workerId = 1;

    // 单例模式，确保只有一个实例

    /**
     * 私有化构造函数，防止外部实例化。
     * 初始化 Yitter 的 ID 生成器。
     *
     *  数据中心或机器 ID（0-63）。
     */
    private IdGenerator( ) {
        IdGeneratorOptions options = new IdGeneratorOptions(workerId);
        // 可根据需要调整配置，比如 SeqBitLength、BaseTime 等
        YitIdHelper.setIdGenerator(options);
    }



    /**
     * 生成唯一 ID。
     *
     * @return 全局唯一的长整型 ID。
     */
    public long nextId() {
        return YitIdHelper.nextId();
    }
}