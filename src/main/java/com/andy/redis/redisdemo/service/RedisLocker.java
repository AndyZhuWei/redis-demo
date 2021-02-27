package com.andy.redis.redisdemo.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @Description 锁管理接口实现类
 * @Author zhuwei
 * @Date 2021/2/22 17:10
 */
@Component
public class RedisLocker implements DistributedLocker{

    private final static String LOCKER_PREFIX = "lock";

    @Autowired
    RedissonConnector redissonConnector;


    @Override
    public <T> T lock(String resourceName, AquiredLockWorker<T> worker) throws Exception {
        return lock(resourceName,worker,100);
    }

    @Override
    public <T> T lock(String resourceName, AquiredLockWorker<T> worker, int lockTime) throws Exception {
        RedissonClient redisson = redissonConnector.getClient();//获取redisson客户端
        RLock lock = redisson.getLock(LOCKER_PREFIX+resourceName);//获取锁
        boolean success = lock.tryLock(100,lockTime, TimeUnit.SECONDS);//尝试加锁
        if(success) {
            try {
                return worker.invokeAfterLockAquire();//获取锁成功之后 执行业务逻辑
            } finally {
                lock.unlock();
            }
        }

        throw new RuntimeException("获取锁失败");
    }
}
