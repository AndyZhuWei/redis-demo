package com.andy.redis.redisdemo;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;

import java.util.*;

/**
 * @Author: zhuwei
 * @Date:2018/7/6 8:50
 * @Description:
 */
public class TestSingleRedis {

    //1.单独连接1台redis服务器
    private static Jedis jedis;
    //2.主从，哨兵使用shard
    private static ShardedJedis shard;
    //3.连接池
    private static ShardedJedisPool pool;

    @BeforeClass
    public static void setUpBeforeClass() {
        //1.单个连接
        jedis = new Jedis("192.168.80.5",6379);

        //2.分片
        List<JedisShardInfo> shards = Arrays.asList(new JedisShardInfo("192.168.80.5",6379));
        shard = new ShardedJedis(shards);

        GenericObjectPoolConfig goConfig = new GenericObjectPoolConfig();
        goConfig.setMaxTotal(100);
        goConfig.setMaxIdle(20);
        goConfig.setMaxWaitMillis(-1);
        goConfig.setTestOnBorrow(true);
        pool = new ShardedJedisPool(goConfig,shards);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        jedis.disconnect();
        shard.disconnect();
        pool.destroy();
    }

    @Test
    public void testString() {
        //添加数据
        jedis.set("name","andy");
        System.out.println(jedis.get("name"));

        jedis.append("name","is my lover");
        System.out.println(jedis.get("name"));

        jedis.del("name");
        System.out.println(jedis.get("name"));


        jedis.mset("name","andy","age","30","qq","878234234");
        jedis.incr("age");
        System.out.println(jedis.get("name")+"-"+jedis.get("age")+"-"+jedis.get("qq"));
    }

    /**
     * redis操作Map
     */
    @Test
    public void testMap() {
        //添加数据
        Map<String,String> map = new HashMap<String,String>();
        map.put("name","jack");
        map.put("age","22");
        map.put("qq","123213");
        jedis.hmset("user",map);
        //取出user中的name
        List<String> rsmap = jedis.hmget("user","name","age","qq");
        System.out.println(rsmap);
        //删除map中的某个键值
        jedis.hdel("user","age");
        System.out.println(jedis.hmget("user","age"));
        System.out.println(jedis.hlen("user"));
        System.out.println(jedis.exists("user"));
        System.out.println(jedis.hkeys("user"));
        System.out.println(jedis.hvals("user"));

        Iterator<String> iter = jedis.hkeys("user").iterator();
        while(iter.hasNext()) {
            String key = iter.next();
            System.out.println(key+":"+jedis.hmget("user",key));
        }

    }


    /**
     * Jedis操作List
     */
    @Test
    public void testList() {
        jedis.del("java framework");
        System.out.println(jedis.lrange("java framework",0,-1));

        jedis.lpush("java framework","spring");
        jedis.lpush("java framework","struts");
        jedis.lpush("java framework","hibernate");


        System.out.println(jedis.lrange("java framework",0,-1));

        jedis.del("java framework");
        jedis.rpush("java framework","spring");
        jedis.rpush("java framework","struts");
        jedis.rpush("java framework","hibernate");

        System.out.println(jedis.lrange("java framework",0,-1));

    }

    /**
     * Jedis操作set
     */
    @Test
    public void testSet() {
        //添加
        jedis.sadd("userSet","liuling");
        jedis.sadd("userSet","xinxin");
        jedis.sadd("userSet","ling");
        jedis.sadd("userSet","zhangsna");
        jedis.sadd("userSet","who");


        jedis.srem("userSet","who");
        System.out.println(jedis.smembers("userSet"));//获取所有加入的value
        System.out.println(jedis.sismember("userSet","who"));//判断who是否是user集合的元素
        System.out.println(jedis.srandmember("userSet"));
        System.out.println(jedis.scard("userSet"));//返回集合元素的个数

    }








}
