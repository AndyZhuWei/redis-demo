package com.andy.redis.redisdemo.service;

import com.andy.redis.redisdemo.model.Person;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.hash.Jackson2HashMapper;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author HP
 * @Description TODO
 * @date 2020/11/25-11:32
 */
@Component
public class TestRedis {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ObjectMapper objectMapper;


    public void testRedis() {

        //opsForValue表示字符串类型操作
        redisTemplate.opsForValue().set("hello","china");

        //可以打印出hello，如果用redis-cli登陆查看，则使用keys * 输出一个乱码的串，并不是hello
        //因为redis是二进制安全的
        //只存储字节数组，任何客户端要注意一个事情，就是我的数据是怎么编程二进制数组的
        System.out.println(redisTemplate.opsForValue().get("hello"));

        stringRedisTemplate.opsForValue().set("stringRedisTemplate","stringRedisTemplate");
        System.out.println(stringRedisTemplate.opsForValue().get("stringRedisTemplate"));

        /////////low api//////////////
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        connection.set("hello02".getBytes(),"zhuwei".getBytes());
        System.out.println(new String(connection.get("hello02".getBytes())));



        HashOperations<String, Object, Object> hash = stringRedisTemplate.opsForHash();
        hash.put("zhuwei","name","andy");
        hash.put("zhuwei","age","12");

        System.out.println(hash.entries("zhuwei"));



        /////////处理对象///////////
        Person person = new Person();
        person.setUserName("zhuwei");
        person.setAge(23);

        Jackson2HashMapper jm = new Jackson2HashMapper(objectMapper,false);

        //hashValue的序列化
        stringRedisTemplate.setHashValueSerializer(new Jackson2JsonRedisSerializer<Object>(Object.class));


        stringRedisTemplate.opsForHash().putAll("andy",jm.toHash(person));
        Map andy = stringRedisTemplate.opsForHash().entries("andy");
        Person person1 = objectMapper.convertValue(andy, Person.class);
        System.out.println(person1.getUserName());


        //发布订阅中的发布
        stringRedisTemplate.convertAndSend("ooxx","1");

        //发布订阅中的订阅
        RedisConnection connection1 = stringRedisTemplate.getConnectionFactory().getConnection();
        connection1.subscribe(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] bytes) {
                byte[] body = message.getBody();
                System.out.println(new String(body));
            }
        },"ooxx".getBytes());

        while (true) {
            stringRedisTemplate.convertAndSend("ooxx","hello from wo zi ji");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }




    }
}
