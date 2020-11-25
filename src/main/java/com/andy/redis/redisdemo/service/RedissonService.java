package com.andy.redis.redisdemo.service;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.stereotype.Service;

/**
 * @Author: zhuwei
 * @Date:2019/9/29 9:54
 * @Description: 参考https://www.jianshu.com/p/47fd7f86c848
 */
@Service
public class RedissonService {

    private  String lock_key = "redis_lock";//锁键

    public  RLock getLock() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.80.100:6379");
        config.useSingleServer().setPassword("andy");

        final RedissonClient client = Redisson.create(config);
        return client.getLock(lock_key);
    }
}
