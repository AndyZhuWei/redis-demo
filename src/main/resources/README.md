# 分布式锁
原理：通过set的扩展参数来执行
    > set lock:codehole true ex 5 nx OK ... do something critical ... 
    > del lock:codehole
java客户端的代码可以参考RedisLock,RedisLock不支持锁的可重入，我们可以使用Redisson框架中的RLock来达到目的

# 延迟队列
异步消息队列：通过redis中的数据结构list可以用作队列，对与list的操作lpop\rpop和lpush\rpush可以达到目的。但是如果队列为空则会导致
    空循环，所以一般是使用带阻塞的blpop\brpop和blpush\brpush来达到目的，但是如果阻塞时间过长会导致服务端断开链接，这样客户端
    就会抛出异常，所以一般在客户端需要捕获异常来进行重试。
延迟队列：延迟队列可以通过Redis的zset(有序列表)来实现。我们将消息序列化成一个字符串作为
zset的value，这个消息的到期处理时间作为score,然后用多个线程轮询zset获取到期的任务进行处理，多个线程是为了保障可用性。
运用场景：如果对加锁失败了可以放入延迟队列中后续在进行重试。





