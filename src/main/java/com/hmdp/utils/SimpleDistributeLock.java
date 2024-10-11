package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SimpleDistributeLock implements ILock {
    private static final String keyPrefix = "lock";
    private static final String instanceId = UUID.randomUUID().toString();
    private String lockName;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleDistributeLock(String lockName, StringRedisTemplate stringRedisTemplate) {
        this.lockName = lockName;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean trylock(Long expireTime, TimeUnit timeUnit, String keySuffix) {
        // 加上实例 + 线程标识
        String threadId = instanceId + Thread.currentThread().getId();
        String key = keyPrefix + lockName + keySuffix;
        Boolean suc = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, expireTime, timeUnit);
        return Boolean.TRUE.equals(suc);
    }

    @Override
    public void unlock(String keySuffix) {
        // 对比实例+线程标识
        String threadId = instanceId + Thread.currentThread().getId();
        String key = keyPrefix + lockName + keySuffix;
        String id = stringRedisTemplate.opsForValue().get(key);
        if (id == null || !id.equals(threadId)) {
            log.info("锁已经不存在.");
            return;
        }
        log.info("成功释放锁" + key + " " + threadId);
        stringRedisTemplate.delete(key);
    }
}
