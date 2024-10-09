package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Shop getByIdRedisWithMutex(Long id) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException;

    Result updateByIdRedis(Shop shop);

    Shop getByIdRedisWithLogicExpire(Long id) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException;
}
