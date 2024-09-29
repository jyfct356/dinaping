package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;


public class RedisUtils {
    public static Map<String, Object> toHash(Object obj) {
        return BeanUtil.beanToMap(obj, new HashMap<>(),
                new CopyOptions()
                        .ignoreNullValue().
                        setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
    }

    public static <T> T toBean(Map<?, ?> map, Class<T> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return BeanUtil.fillBeanWithMap(map, clazz.getDeclaredConstructor().newInstance(), false);
    }



}
