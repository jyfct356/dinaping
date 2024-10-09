package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisClient {
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1728480624L;
    private static final long MOVE_BITS = 32L;

    RedisClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Long nextId(String serviceKey) {
//        System.out.println("come in nextId.");
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "id:" + serviceKey + ":" + date;
//        System.out.println("key = " + key);

        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        long seq = stringRedisTemplate.opsForValue().increment(key);
//        System.out.println("get id = " + ((timestamp << MOVE_BITS) | seq));
        return (timestamp << MOVE_BITS) | seq;
    }

}
