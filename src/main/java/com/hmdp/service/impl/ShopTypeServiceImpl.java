package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> getTypeListRedis() {
        log.info("查询店铺类型列表.");
        String typeListJSON = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOPTYPE_KEY);
        if (typeListJSON != null) {
            return JSONUtil.toList(typeListJSON, ShopType.class);
        }
        List<ShopType> typeList = query().list();
        if (typeList != null) {
            log.info("save shoptype in redis.");
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOPTYPE_KEY, JSONUtil.toJsonStr(typeList));
        }
        return typeList;
    }
}
