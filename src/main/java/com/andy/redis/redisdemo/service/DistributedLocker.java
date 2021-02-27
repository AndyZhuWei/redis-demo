package com.andy.redis.redisdemo.service;

/**
 * @Description 获取锁管理类，封装锁管理接口
 * @Author zhuwei
 * @Date 2021/2/22 17:07
 */
public interface DistributedLocker {

    /**
     * 获取锁
     * @param resourceName 锁的名称
     * @param worker 获取锁后的处理类
     * @param <T>
     * @return 处理完具体的业务逻辑后需要返回的数据
     * @throws Exception
     */
    <T> T lock(String resourceName,AquiredLockWorker<T> worker) throws Exception;

    <T> T lock(String resourceName,AquiredLockWorker<T> worker,int lockTime) throws Exception;
}
