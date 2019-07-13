package com.andy.redis.redisdemo;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisClusterCommand;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashSet;
import java.util.Set;

public class TestClusterRedis {

    public static void main(String[] args) {
        Set<HostAndPort> jedisClusterNode = new HashSet<HostAndPort>();
        jedisClusterNode.add(new HostAndPort("192.168.80.100",7001));
        jedisClusterNode.add(new HostAndPort("192.168.80.100",7002));
        jedisClusterNode.add(new HostAndPort("192.168.80.100",7003));
        jedisClusterNode.add(new HostAndPort("192.168.80.100",7004));
        jedisClusterNode.add(new HostAndPort("192.168.80.100",7005));
        jedisClusterNode.add(new HostAndPort("192.168.80.100",7006));

//        GenericObjectPoolConfig goConfig = new GenericObjectPoolConfig();
//        JedisCluster jc = new JedisCluster(jedisClusterNode,2000,100,goConfig);
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(100);
        cfg.setMaxIdle(20);
        cfg.setMaxWaitMillis(-1);
        cfg.setTestOnBorrow(true);
        JedisCluster jc = new JedisCluster(jedisClusterNode,6000,100,cfg);

        System.out.println(jc.set("age","20"));
        System.out.println(jc.set("sex","nv"));
        System.out.println(jc.get("name"));
        System.out.println(jc.get("age"));
        System.out.println(jc.get("sex"));

//        jc.close();

//        JedisClusterCommand<HostAndPort> jedisClusterNode;
//        jedisClusterNode.execute(arg0)






    }
}
