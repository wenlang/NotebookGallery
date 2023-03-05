package com.wenlang.notebook.config.ratelimit.distribute;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RedisRateLimiterFactory {
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 用WeakHashMap作为RedisRateLimiter缓存容器的，是为了垃圾收集器能回收长期没有使用的RedisRateeLimiter对象，防止内存泄漏
     */
    private final WeakHashMap<String, RedisRateLimiter> limiterMap = new WeakHashMap<String, RedisRateLimiter>();
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();


    public RedisRateLimiter get(String keyPrefix, TimeUnit timeUnit, int permits) {
        RedisRateLimiter redisRateLimiter = null;
        try {
            lock.readLock().lock();
            if (limiterMap.containsKey(keyPrefix)) {
                redisRateLimiter = limiterMap.get(keyPrefix);
            }
        } finally {
            lock.readLock().unlock();
        }

        if (redisRateLimiter == null) {
            try {
                lock.writeLock().lock();
                if (limiterMap.containsKey(keyPrefix)) {
                    redisRateLimiter = limiterMap.get(keyPrefix);
                }
                if (redisRateLimiter == null) {
                    redisRateLimiter = new RedisRateLimiter(redisTemplate, timeUnit, permits);
                    limiterMap.put(keyPrefix, redisRateLimiter);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        return redisRateLimiter;
    }


    public RedisTemplate getRedisTemplate() {
        return redisTemplate;
    }

    public void setRedisTemplate(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
