package com.andy.redis.redisdemo;

import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * @Author: zhuwei
 * @Date:2018/7/6 8:56
 * @Description:
 */
public class TestRedis {

    @Test
    public void test() {
        Jedis j = new Jedis("192.168.80.5",6379);
//      System.out.println(j);

       //User对象数据量很大，查询很频繁，把User表里的数据都放在缓存中去
        //可以想象存入了500w的数据，现在我们要做的事情就是
        //select * from SYS_USER_TABLE WHERE AGE=25的数据取出来。
        //select * from SYS_USER_TABLE WHERE AGE=25 and sex='f'的数据取出来。

        final String SYS_USER_TABLE = "SYS_USER_TABLE";

        //多种集合配合使用 hash和set类型同时使用

        //指定查询业务：SYS_USER_SEL_AGE_25
        //指定查询业务：SYS_USER_SEL_SEX_F
        //指定查询业务：SYS_USER_SEL_SEX_M

        final String SYS_USER_SEL_AGE_25 = "SYS_USER_SEL_AGE_25";
        final String SYS_USER_SEL_SEX_F = "SYS_USER_SEL_SEX_F";
        final String SYS_USER_SEL_SEX_M = "SYS_USER_SEL_SEX_M";


       //做放入操作
        Map<String,String> map = new HashMap<String,String>();
        User user1 = new User(UUID.randomUUID().toString(),"zs1",21,"m");
        j.sadd(SYS_USER_SEL_SEX_M,user1.getId());
        User user2 = new User(UUID.randomUUID().toString(),"zs2",22,"m");
        j.sadd(SYS_USER_SEL_SEX_M,user2.getId());
        User user3 = new User(UUID.randomUUID().toString(),"zs3",23,"f");
        j.sadd(SYS_USER_SEL_SEX_F,user3.getId());
        User user4 = new User(UUID.randomUUID().toString(),"zs4",24,"f");
        j.sadd(SYS_USER_SEL_SEX_F,user4.getId());
        User user5 = new User(UUID.randomUUID().toString(),"zs5",25,"f");
        j.sadd(SYS_USER_SEL_SEX_F,user5.getId());
        j.sadd(SYS_USER_SEL_AGE_25,user5.getId());
        User user6 = new User(UUID.randomUUID().toString(),"zs6",26,"f");
        j.sadd(SYS_USER_SEL_SEX_F,user6.getId());
        User user7 = new User(UUID.randomUUID().toString(),"zs7",27,"m");
        j.sadd(SYS_USER_SEL_SEX_M,user7.getId());
        User user8 = new User(UUID.randomUUID().toString(),"zs8",28,"m");
        j.sadd(SYS_USER_SEL_SEX_M,user8.getId());
        User user9 = new User(UUID.randomUUID().toString(),"zs9",29,"f");
        j.sadd(SYS_USER_SEL_SEX_F,user9.getId());
        User user10 = new User(UUID.randomUUID().toString(),"zs10",30,"f");
        j.sadd(SYS_USER_SEL_SEX_F,user10.getId());

        map.put(user1.getId(),FastJsonUtils.toJSONString(user1));
        map.put(user2.getId(),FastJsonUtils.toJSONString(user2));
        map.put(user3.getId(),FastJsonUtils.toJSONString(user3));
        map.put(user4.getId(),FastJsonUtils.toJSONString(user4));
        map.put(user5.getId(),FastJsonUtils.toJSONString(user5));
        map.put(user6.getId(),FastJsonUtils.toJSONString(user6));
        map.put(user7.getId(),FastJsonUtils.toJSONString(user7));
        map.put(user8.getId(),FastJsonUtils.toJSONString(user8));
        map.put(user9.getId(),FastJsonUtils.toJSONString(user9));
        map.put(user10.getId(),FastJsonUtils.toJSONString(user10));

        j.hmset(SYS_USER_TABLE,map);
//select * from SYS_USER_TABLE WHERE AGE=25的数据取出来。
        //指定查询业务：SYS_USER_SEL_AGE_25
        //查询SYS_USER_SEL_AGE_25
        Set<String> user_ages = j.smembers(SYS_USER_SEL_AGE_25);
        System.out.println(user_ages);
        String[] a = new String[user_ages.size()];
        List<String> ret = j.hmget(SYS_USER_TABLE,user_ages.toArray(a));
        for (String s : ret) {
            System.out.println(s);
        }
        //指定查询业务：SYS_USER_SEL_SEX_F
        Set<String> user_sex_f = j.smembers(SYS_USER_SEL_SEX_F);
        System.out.println(user_sex_f);
        String[] b = new String[user_sex_f.size()];
        List<String> retb = j.hmget(SYS_USER_TABLE,user_sex_f.toArray(b));
        for (String s : retb) {
            System.out.println(s);
        }
        //select * from SYS_USER_TABLE WHERE AGE=25 and sex='f'的数据取出来。
        Set<String> user_age_25_sex_f = j.sinter(SYS_USER_SEL_AGE_25,SYS_USER_SEL_SEX_F);
        System.out.println(user_age_25_sex_f);
        String[] c = new String[user_age_25_sex_f.size()];
        List<String> retc = j.hmget(SYS_USER_TABLE,user_age_25_sex_f.toArray(c));
        for (String s : retc) {
            System.out.println(s);
            User user = FastJsonUtils.toBean(s,User.class);
            System.out.println("age:"+user.getAge());
            System.out.println("name:"+user.getName());
            System.out.println("sex:"+user.getSex());

        }

    }
}
