package com.andy.redis.redisdemo.service.sentinel;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;

import java.util.HashSet;
import java.util.Set;

/**
 * @Description 使用JRedis操作Sentinel集群
 * @Author zhuwei
 * @Date 2021/2/24 11:08
 */
public class JRedisSentinelService {

    public static void main(String[] args) {
        String ip = "192.168.80.100";
        Set sentinels = new HashSet<>();
        sentinels.add(new HostAndPort(ip,26379).toString());
        sentinels.add(new HostAndPort(ip,26380).toString());
        sentinels.add(new HostAndPort(ip,26381).toString());

        JedisSentinelPool sentinelPool = new JedisSentinelPool("mymaster",sentinels);
        System.out.println("Current master: "+sentinelPool.getCurrentHostMaster().toString());
        Jedis master = sentinelPool.getResource();
        master.set("username","andy1");
        master.close();
        sentinelPool.destroy();
    }





}
