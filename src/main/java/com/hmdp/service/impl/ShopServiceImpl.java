package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jni.Time;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Qualifier("redisTemplate")
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 互斥锁方式解决缓存击穿
     * @param id
     * @return
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     */
    @Override
    public Shop getByIdRedisWithMutex(Long id) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        log.info("根据id查询店铺.");
        // 查询缓存，有就直接返回
        String shopJSON = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJSON)) {
            System.out.println("返回缓存结果!");
            return JSONUtil.toBean(shopJSON, Shop.class);
        }
        // 返回空值 防止缓存穿透
        if (shopJSON != null) {
            System.out.println("返回空值！");
            return null;
        }

        // 缓存击穿 互斥锁方式
        Shop shop = null;
        try {
            // 获取锁
            if (!tryLock(id)) {
                // 没有锁，休眠重试
                System.out.println("获取锁失败！");
                Thread.sleep(50);
                return getByIdRedisWithMutex(id);
            }
            System.out.println("获取锁成功！");
            // 查询数据库
            shop = getById(id);
            // 有，更新缓存，返回
            if (shop != null) {
                log.info("save shop info to redis.");
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL , TimeUnit.MINUTES);
            } else {
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_SHOP_TTL , TimeUnit.MINUTES);
            }
            log.info("get shop info: " + shop);
            // 释放锁
            System.out.println("释放锁!");
            unlock(id);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return shop;
    }

    private final ExecutorService cacheRebuildPool = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期方式解决缓存击穿
     * @param id
     * @return
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     */
    public Shop getByIdRedisWithLogicExpire(Long id) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        log.info("根据id查询店铺.");
        // 查询缓存
        String redisDataJSON = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 查询不到则构建缓存
        if (redisDataJSON == null) {
            // 构建缓存
            if (tryLock(id)) {
                cacheRebuildPool.submit(() -> {
                    this.updateRedis(id);
                });
            }
            // 先返回空值
            return null;
        }
        // 查询到，则检查过期时间
        RedisData redisData = JSONUtil.toBean(redisDataJSON, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        if (LocalDateTime.now().isBefore(redisData.getExpireTime())) {
            System.out.println("没有过期，返回缓存结果!");
            return shop;
        }

        // 数据已经逻辑过期，构建缓存
        if (!tryLock(id)) {
            return shop;
        }
        cacheRebuildPool.submit(() -> {
            this.updateRedis(id);
        });
        unlock(id);
        return shop;
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 查询距离最近的
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        Integer begin = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        Integer end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResultsContent = geoResults.getContent();
        if (geoResultsContent == null || geoResultsContent.isEmpty()) {
            return Result.ok();
        }
        List<String> ids = new ArrayList<>(geoResultsContent.size());
        Map<String, Distance> distanceMap = new HashMap<>(geoResultsContent.size());
        geoResultsContent.stream().skip(begin).forEach(result -> {
            String shopId = result.getContent().getName();
            Distance distance = result.getDistance();
            ids.add(shopId);
            distanceMap.put(shopId, distance);
        });
        String idsStr = String.join(",", ids);
        List<Shop> list = query().in("id", ids).last("order by field(id, " + idsStr + ")").list();
        for (Shop shop : list) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(list);
    }

    public void updateRedis(Long id) {
        System.out.println("重构缓存.");
        // 查询数据库
        Shop shop = getById(id);
        // 更新缓存,shop是否空值的过期时间不同
        RedisData redisData = null;
        if (shop != null) {
            redisData = new RedisData(LocalDateTime.now().plusSeconds(RedisConstants.CACHE_SHOP_TTL), shop);
        } else {
            // 防止缓存穿透，过期时间较短
            redisData = new RedisData(LocalDateTime.now().plusSeconds(RedisConstants.CACHE_NULL_TTL), shop);
        }
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    public boolean tryLock(Long id) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, "", RedisConstants.LOCK_SHOP_TTL , TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(Long id) {
        stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY + id);
    }

    @Override
    @Transactional
    public Result updateByIdRedis(Shop shop) {
        // 数据校验
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空.");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
