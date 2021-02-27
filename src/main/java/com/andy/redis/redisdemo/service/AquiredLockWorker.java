package com.andy.redis.redisdemo.service;

/**
 * @Description 获取锁后需要处理的逻辑,业务处理接口
 * @Author zhuwei
 * @Date 2021/2/22 17:06
 */
public interface AquiredLockWorker<T> {

    T invokeAfterLockAquire() throws Exception;
}
