package com.andy.redis.redisdemo.service;

import redis.clients.jedis.Jedis;

import java.util.Map;

/**
 * @Author: zhuwei
 * @Date:2019/9/26 14:16
 * @Description: 可重入锁
 */
public class RedisWithReentrantLock {
    private ThreadLocal<Map> lockers = new ThreadLocal<Map>();
    private Jedis jedis;

    public RedisWithReentrantLock(Jedis jedis) {
        this.jedis=jedis;
    }







}
