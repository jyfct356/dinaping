package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SimpleDistributeLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jni.Time;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisClient redisClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    private static DefaultRedisScript<Long> seckillScript;
    static {
        seckillScript = new DefaultRedisScript();
        seckillScript.setResultType(Long.class);
        seckillScript.setLocation(new ClassPathResource("seckill.lua"));
    }
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy;




    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitListenerEndpointRegistry registry;


    @RabbitListener(id = "orderGenerator", queues = "dianping.seckill", autoStartup = "false")
    public void orderGenerator(VoucherOrder voucherOrder) {
        log.info("generator work " + voucherOrder);
//        proxy.createVoucherOrder(voucherOrder);
    }

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        proxy  = (IVoucherOrderService) AopContext.currentProxy();
        // 应用启动完成后执行的逻辑
        log.info("启动异步handler.");
        executor.submit(new VoucherOrderHandler());
    }

//    @PostConstruct
//    void init() {
//        log.info("启动异步handler.");
//        log.info("" + registry.getListenerContainerIds());
//        MessageListenerContainer container = registry.getListenerContainer("orderGenerator");
//        if (container != null) {
//            container.start();
//        } else {
//            log.error("Listener - seckill.handler is null");
//        }
////        executor.submit(new VoucherOrderHandler());
//    }

    public class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.info("async handler run...");
            MessageListenerContainer container = registry.getListenerContainer("orderGenerator");
            if (container != null) {
                container.start();
                log.info("orderGenrator started now...");
            } else {
                log.error("Listener - seckill.handler is null");
            }
        }

    }



    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        String keySuffix = UserHolder.getUser().getId().toString() + ":" + voucherOrder.getVoucherId();
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_USER_SECKILL_KEY + keySuffix);
        try {
            boolean suc = lock.tryLock(RedisConstants.LOCK_USER_SECKILL_RETRY_TTL, RedisConstants.LOCK_USER_SECKILL_TTL, TimeUnit.SECONDS);
            if (!suc) {
                log.error("不允许重复下单.");
            }
            log.info("准备生成订单.");
            proxy.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result buySeckillVoucher(Long voucherId) {
        log.info("秒杀开始.");
        // 检查秒杀时间
//        LocalDateTime now = LocalDateTime.now();
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        if (now.isBefore(seckillVoucher.getBeginTime()) || now.isAfter(seckillVoucher.getEndTime())) {
//            return Result.fail("不在秒杀活动时间范围.");
//        }
//        // 检查库存
//        if (seckillVoucher.getStock() <= 0) {
//            return Result.fail("库存不足.");
//        }
        // TODO 在redis完成秒杀资格判断和预下单
        Long userId = UserHolder.getUser().getId();
        List<String> keyList = new ArrayList();
        keyList.add(RedisConstants.SECKILL_STOCK_KEY);
        keyList.add(RedisConstants.SECKILL_ORDER_KEY);
        keyList.add(RedisConstants.SECKILL_BEGINTIME_KEY);
        keyList.add(RedisConstants.SECKILL_ENDTIME_KEY);
        Long result = stringRedisTemplate.execute(seckillScript,
                keyList, userId.toString(), voucherId.toString(), String.valueOf(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()));
        if (result == 1) {
            log.error("不在秒杀时间.");
            return  Result.fail("不在秒杀时间.");
        } else if (result == 2) {
            log.error(("用户重复下单."));
            return Result.fail("用户重复下单.");
        } else if (result == 3) {
            log.error("库存不足.");
            return Result.fail("库存不足.");
        }

        // TODO 保存到消息队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(redisClient.nextId(RedisConstants.SECKILL_GEN_ID_KEY));
//        orderTasks.add(voucherOrder);
        String queueName = "dianping.seckill";
        rabbitTemplate.convertAndSend(queueName, voucherOrder);

        return Result.ok(voucherOrder.getId());

//        // synchronized 锁不住后端集群
//        synchronized (UserHolder.getUser().getId().toString().intern()) {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        // s分布式锁
//        String keySuffix = UserHolder.getUser().getId().toString() + ":" + voucherId;
////        SimpleDistributeLock lock = new SimpleDistributeLock(RedisConstants.LOCK_USER_SECKILL_KEY, stringRedisTemplate);
//        RLock lock = redissonClient.getLock(RedisConstants.LOCK_USER_SECKILL_KEY + keySuffix);
//        try {
//            boolean suc = lock.tryLock(RedisConstants.LOCK_USER_SECKILL_RETRY_TTL, RedisConstants.LOCK_USER_SECKILL_TTL, TimeUnit.SECONDS);
//            if (!suc) {
//                return Result.fail("用户已经下单中.");
//            }
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock();
//        }
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        log.info("开始生成订单.");
        // 一人一单
        // 兜底
        Long voucherId = voucherOrder.getVoucherId();
        int count = query().eq("voucher_id",voucherId).eq("user_id", voucherOrder.getUserId()).count();
        if (count > 0) {
            log.info("用户已经到达下单上限.");
//            return;
        }
        // 扣减库存
        boolean buySeccuss = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!buySeccuss) {
            log.error("扣库存失败.");
//            return ;
        }
        //下单
        save(voucherOrder);
        log.info("下单成功.");
    }
}
