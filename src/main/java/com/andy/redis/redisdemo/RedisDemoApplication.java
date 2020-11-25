package com.andy.redis.redisdemo;

import com.andy.redis.redisdemo.service.TestRedis;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class RedisDemoApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(RedisDemoApplication.class, args);

		TestRedis testRedis = context.getBean(TestRedis.class);
		testRedis.testRedis();

	}
}
