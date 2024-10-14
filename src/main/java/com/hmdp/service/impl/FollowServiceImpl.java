package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private IUserService userService;

    @Autowired
    private final StringRedisTemplate stringRedisTemplate;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result follow(Long followId, Boolean isFollow) {
        Long id = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOWER_KEY + id;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(id);
            follow.setFollowUserId(followId);
            boolean suc = save(follow);
            if (!suc) {
                return Result.fail("关注失败，请重试.");
            }
            stringRedisTemplate.opsForSet().add(key, followId.toString());
        } else {
            boolean suc = remove(new QueryWrapper<Follow>()
                    .eq("user_id", id)
                    .eq("follow_user_id", followId));
            if (!suc) {
                return Result.fail("取关失败，请重试.");
            }
            stringRedisTemplate.opsForSet().remove(key, followId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followId) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOWER_KEY + userId;
        Boolean ismember = stringRedisTemplate.opsForSet().isMember(key, followId.toString());
        if (ismember) {
            return Result.ok(ismember);
        }
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followId).count();
        if (count <= 0) {
            return Result.ok(false);
        }
        stringRedisTemplate.opsForSet().add(key, followId.toString());
        return Result.ok(true);
    }

    @Override
    public Result common(Long blogUserId) {
        String key1 = RedisConstants.FOLLOWER_KEY + UserHolder.getUser().getId();
        String key2 = RedisConstants.FOLLOWER_KEY + blogUserId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> userDTOS = userService.listByIds(intersect).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }


}
