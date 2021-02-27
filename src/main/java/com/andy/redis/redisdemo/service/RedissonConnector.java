package com.andy.redis.redisdemo.service;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @Description 获取RedissonClient连接类
 * @Author zhuwei
 * @Date 2021/2/22 16:59
 * 参见 https://zhuanlan.zhihu.com/p/148499134
 */
@Component
public class RedissonConnector {

    RedissonClient redisson;

    @PostConstruct
    public void init() {

        //集群模式
      /*  Config config = new Config();
        config.useSentinelServers().addSentinelAddress("127.0.0.1:6369","127.0.0.1:6379","127.0.0.1:6389")
                .setMasterName("masterName")
                .setPassword("password").setDatabase(0);
        RedissonClient redissonClient = Redisson.create(config);*/

        //单机模式
        redisson = Redisson.create();
    }

    public RedissonClient getClient() {
        return redisson;
    }

}
