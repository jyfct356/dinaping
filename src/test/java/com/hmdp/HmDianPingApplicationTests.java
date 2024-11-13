package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.RedisConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest(classes = HmDianPingApplication.class)
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {
    @Autowired
    private RedisClient redisClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IShopService shopService;

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

    @Test
    public void upShopLocationToRedis() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> shopByType = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : shopByType.entrySet()) {
            String key = RedisConstants.SHOP_GEO_KEY + entry.getKey();
            List<Shop> shopList = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shopList.size());
            for (Shop shop : shopList) {
//                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }

    }

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;

    @Test
    public void testRabbitMQ() {
        String queueName = "dianping.seckill";
        for (int i = 0; i < 100000; i++) {
            String msg = "hello world";
//            System.out.println(rabbitListenerEndpointRegistry.getListenerContainerIds());
            rabbitTemplate.convertAndSend(queueName, msg);
        }
        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @RabbitListener(id = "hahah", queues = "dianping.seckill")
    public void receiveMessage(String message) {
        System.out.println(message);
    }

}
