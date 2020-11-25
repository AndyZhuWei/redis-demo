package com.andy.redis.redisdemo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: zhuwei
 * @Date:2019/9/26 14:40
 * @Description:
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {RedisDemoApplication.class})
@AutoConfigureMockMvc
@EnableAutoConfiguration
public class LockTest {

    @Autowired
    private MockMvc mockMvc;


    @Test
    public void testLock() {
        String data = "";
        RequestBuilder rb = MockMvcRequestBuilders.post("/lock/test")
                .contentType(MediaType.APPLICATION_JSON_UTF8).content(data).accept(MediaType.APPLICATION_JSON_UTF8);


        try {
            mockMvc
                    .perform(rb)
                    .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8)).andDo(print())
                    .andReturn();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
