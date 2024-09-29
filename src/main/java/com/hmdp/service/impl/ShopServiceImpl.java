package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

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

    @Override
    public Shop getByIdRedis(Long id) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        log.info("根据id查询店铺.");
        // 查询缓存，有就直接返回
        String shopJSON = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (shopJSON != null) {
            return JSONUtil.toBean(shopJSON, Shop.class);
        }
        // 查询数据库
        Shop shop = getById(id);
        // 有，更新缓存，返回
        if (shop != null) {
            log.info("save shop info to redis.");
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        }
        log.info("get shop info: " + shop);
        return shop;
    }
}
