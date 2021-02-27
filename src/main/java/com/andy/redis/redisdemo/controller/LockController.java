package com.andy.redis.redisdemo.controller;

import com.andy.redis.redisdemo.service.AquiredLockWorker;
import com.andy.redis.redisdemo.service.RedisLock;
import com.andy.redis.redisdemo.service.RedisLocker;
import com.andy.redis.redisdemo.service.RedissonService;
import com.andy.redis.redisdemo.util.SnowflakeIdWorker;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.concurrent.*;

/**
 * @Author: zhuwei
 * @Date:2019/9/25 17:09
 * @Description: 锁Controller
 */
@Controller
@RequestMapping("/lock")
public class LockController {

    private Logger logger = LoggerFactory.getLogger(LockController.class);

    @Autowired
    RedisLock redisLock;

    @Autowired
    private RedissonService redissonService;

    @Autowired
    private RedisLocker distributedLocker;

    int count = 0;

    /**
     * redis分布式锁，不支持可重入
     * 使用jedis在单实例上的演示
     * @return
     * @throws InterruptedException
     */
    @PostMapping("/test")
    @ResponseBody
    public String lock() throws InterruptedException {
        int clientcount = 1000;
        CountDownLatch countDownLatch = new CountDownLatch(clientcount);

        //ExecutorService executorService = Executors.newFixedThreadPool(clientcount);
        ExecutorService executorService = new ThreadPoolExecutor(clientcount, clientcount,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());

        long start  = System.currentTimeMillis();
        for(int i=0;i<clientcount;i++) {
            executorService.execute(() -> {

                //通过Snowflake算法获取唯一的ID字符串
                String id = SnowflakeIdWorker.generateId()+"";
                try {
                    redisLock.lock(id);
                    count++;
                } finally {
                    redisLock.unlock(id);
                }
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        logger.info("执行线程数:{},总耗时:{},count数为:{}",clientcount,end-start,count);
        return "Hello";
    }



    /**
     * 使用RedissonClient客户端
     * redis分布式锁，支持可重入
     * @return
     * @throws InterruptedException
     * 原理参考 https://www.jianshu.com/p/47fd7f86c848
     */
    @PostMapping("/redissonClient")
    @ResponseBody
    public String redissonClient() throws InterruptedException {
        int clientcount = 1000;
        CountDownLatch countDownLatch = new CountDownLatch(clientcount);

        RLock rLock = redissonService.getLock();

        //ExecutorService executorService = Executors.newFixedThreadPool(clientcount);
        ExecutorService executorService = new ThreadPoolExecutor(clientcount, clientcount,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());

        long start  = System.currentTimeMillis();
        for(int i=0;i<clientcount;i++) {
            executorService.execute(() -> {

                //通过Snowflake算法获取唯一的ID字符串
                String id = SnowflakeIdWorker.generateId()+"";
                try {
                    rLock.lock();
                    count++;
                } finally {
                    rLock.unlock();
                }
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        logger.info("执行线程数:{},总耗时:{},count数为:{}",clientcount,end-start,count);
        return "Hello";
    }

    /**
     * 使用RedissonClient客户端
     *  进行redlock测试，也就是集群环境下的获取锁
     * @return
     *
     *
     * 使用redession实现分布锁的过程
     * 假设有5个完全独立的redis主服务器
     * 1.获取当前时间戳
     * 2.client尝试按照顺序使用相同的key,value获取所有redis服务的锁，在获取锁的过程中的获取时间比锁过期时间短很多，这是为了不要过长时间等待已经关闭的redis服务。并且试着获取下一个redis实例。
     * 比如：TTL为5s,设置获取锁最多用1s，所以如果一秒内无法获取锁，就放弃获取这个锁，从而尝试获取下个锁
     * 3.client通过获取所有能获取的锁后的时间减去第一步的时间，这个时间差要小于TTL时间并且至少有3个redis实例成功获取锁，才算真正的获取锁成功
     * 4.如果成功获取锁，则锁的真正有效时间是 TTL减去第三步的时间差 的时间；比如：TTL 是5s,获取所有锁用了2s,则真正锁有效时间为3s(其实应该再减去时钟漂移);
     * 5.如果客户端由于某些原因获取锁失败，便会开始解锁所有redis实例；因为可能已经获取了小于3个锁，必须释放，否则影响其他client获取锁
     *
     *
     *
     *原理参考 http://redis.cn/topics/distlock.html
     *
     */
    @RequestMapping(value="/redLockTest")
    public String redLockTest() throws Exception {


        return "redlock";

    }


}
