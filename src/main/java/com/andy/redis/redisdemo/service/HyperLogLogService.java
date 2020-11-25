package com.andy.redis.redisdemo.service;

import redis.clients.jedis.Jedis;

/**
 * @Author: zhuwei
 * @Date:2019/9/29 17:14
 * @Description: HyperLogLog演示
 */
public class HyperLogLogService {

    private final static String host = "192.168.80.100";

    private final static int port = 6379;

    public static void main(String[] args) {
        test1();
    }

    private static void test1() {
        Jedis jedis = new Jedis(host,port);
        for(int i=0;i<1000;i++) {
            jedis.pfadd("codehole","user"+i);
            long total = jedis.pfcount("codehole");
            if(total != i+1) {
                System.out.printf("%d %d\n",total,i+1);
                break;
            }
        }

        jedis.close();
    }

    private static void test2() {
        Jedis jedis = new Jedis(host,port);
        for(int i=0;i<100000;i++) {
            jedis.pfadd("codehole","user"+i);
        }
        long total = jedis.pfcount("codehole");
        System.out.printf("%d %d\n",100000,total);
        jedis.close();
    }
}
