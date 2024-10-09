package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest(classes = HmDianPingApplication.class)
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {
    @Autowired
    private RedisClient redisClient;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    public void testRedisGenId() throws InterruptedException {
        System.out.println("id = " + redisClient.nextId("order"));
        CountDownLatch latch = new CountDownLatch(100);
        System.out.println("test begin.");
        for (int i = 0; i < 100; i++) {
            System.out.println("run a thread.");
            executorService.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    System.out.println("gen id");
                    long id = redisClient.nextId("order");
                    System.out.println("id = " + id);
                    latch.countDown();
                }
            });
        }
        System.out.println("latch " + latch.toString() );
        latch.await();
        System.out.println("finish.");
    }

}
