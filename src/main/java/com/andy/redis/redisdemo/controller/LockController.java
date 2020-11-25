package com.andy.redis.redisdemo.controller;

import com.andy.redis.redisdemo.service.RedisLock;
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

    int count = 0;

    /**
     * redis分布式锁，不支持可重入
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


}
