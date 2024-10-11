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
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

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

    @Override
    public Result buySeckillVoucher(Long voucherId) {
        log.info("秒杀开始.");
        // 检查秒杀时间
        LocalDateTime now = LocalDateTime.now();
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (now.isBefore(seckillVoucher.getBeginTime()) || now.isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("不在秒杀活动时间范围.");
        }
        // 检查库存
        if (seckillVoucher.getStock() <= 0) {
            return Result.fail("库存不足.");
        }
//        // synchronized 锁不住后端集群
//        synchronized (UserHolder.getUser().getId().toString().intern()) {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        // s分布式锁
        SimpleDistributeLock lock = new SimpleDistributeLock(RedisConstants.LOCK_USER_SECKILL_KEY, stringRedisTemplate);
        String keySuffix = UserHolder.getUser().getId().toString() + ":" + voucherId;
        try {
            boolean suc = lock.trylock(RedisConstants.LOCK_USER_SECKILL_TTL, TimeUnit.SECONDS, keySuffix);
            if (!suc) {
                return Result.fail("用户已经下单中.");
            }
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock(keySuffix);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单
        int count = query().eq("voucher_id",voucherId).eq("user_id", UserHolder.getUser().getId()).count();
        if (count > 0) {
            log.info("用户已经到达下单上限.");
            return Result.fail("用户已经到达下单上限.");
        }
        // 扣减库存
        boolean buySeccuss = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!buySeccuss) {
            return Result.fail("扣库存失败.");
        }
        //下单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(redisClient.nextId(RedisConstants.SECKILL_GEN_ID_KEY));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        log.info("下单成功.");
        //返回订单id
        return Result.ok(voucherOrder.getId());
    }
}
