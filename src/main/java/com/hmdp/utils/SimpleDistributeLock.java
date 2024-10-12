package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SimpleDistributeLock implements ILock {
    private static final String KEY_PREFIX = "lock";
    private static final String INSTANCE_ID = UUID.randomUUID().toString();
    private static final DefaultRedisScript<Long> DEFAULT_SCRIPT;
    static {
        DEFAULT_SCRIPT = new DefaultRedisScript<>();
        DEFAULT_SCRIPT.setResultType(Long.class);
        DEFAULT_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    }
    private String lockName;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleDistributeLock(String lockName, StringRedisTemplate stringRedisTemplate) {
        this.lockName = lockName;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean trylock(Long expireTime, TimeUnit timeUnit, String keySuffix) {
        // 加上实例 + 线程标识
        String threadId = INSTANCE_ID + Thread.currentThread().getId();
        String key = KEY_PREFIX + lockName + keySuffix;
        Boolean suc = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, expireTime, timeUnit);
        return Boolean.TRUE.equals(suc);
    }

    @Override
    public void unlock(String keySuffix) {
        // 对比实例+线程标识
        String threadId = INSTANCE_ID + Thread.currentThread().getId();
        String key = KEY_PREFIX + lockName + keySuffix;
        Long suc = stringRedisTemplate.execute(DEFAULT_SCRIPT,
                Collections.singletonList(key),
                threadId);
        if (suc == 1) {
            log.info("成功释放锁" + key + " " + threadId);
        }
    }
}
