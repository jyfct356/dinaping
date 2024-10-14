package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.controller.BlogController;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;
    @Autowired
    private BlogController blogController;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        // 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("blog不存在.");
        }
        queryBlogUser(blog);
        blogIsLiked(blog);
        return Result.ok(blog);
    }

    public void blogIsLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            log.error("用户未登录，不需要查询是否点赞.");
            return ;
        }
        Long userId = user.getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // redis 判断用户是否点过赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 未点赞 点赞
            boolean suc = update().setSql("liked = liked + 1").eq("id", id).update();
            if (!suc) {
                return Result.fail("点赞失败.");
            }
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        } else {
            // 点赞了 取消
            boolean suc = update().setSql("liked = liked - 1").eq("id", id).update();
            if (!suc) {
                return Result.fail("取消点赞失败.");
            }
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogTop5Likes(Long id) {
        // 查询出前五个点赞的userid -> ids
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> ids = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (ids == null || ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        String idsStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("order by field(id, " + idsStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean suc = save(blog);
        if (!suc) {
            return Result.fail("保存博客失败.");
        }
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId().toString()).list();
        List<Long> followedUserIds = follows.stream().map(Follow::getUserId).collect(Collectors.toList());
        for (Long fid : followedUserIds) {
            String key = RedisConstants.FEED_KEY + fid;
            Boolean addsuc = stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
            if (!addsuc) {
                log.error("failed to add blog_" + blog.getId() + " to followed_user_" + fid);
            }
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result getBlogOfFollow(Long offset, Long lastId) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> reversedRangeByScoreWithScores = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, lastId, offset, SystemConstants.MAX_PAGE_SIZE);
        if (reversedRangeByScoreWithScores == null || reversedRangeByScoreWithScores.isEmpty()) {
            return Result.ok();
        }
        List<String> ids = new ArrayList<>(reversedRangeByScoreWithScores.size());
        Long min_timestampMills = 100000000000000000L;
        Integer min_time_count = 1;
        for (ZSetOperations.TypedTuple<String> blogIdWithScores: reversedRangeByScoreWithScores) {
            String blogId = blogIdWithScores.getValue();
            Long timestampMills = blogIdWithScores.getScore().longValue();
            ids.add(blogId);
            if (timestampMills < min_timestampMills) {
                min_time_count = 1;
                min_timestampMills = timestampMills;
            } else {
                min_time_count += 1;
            }
        }
        List<Blog> blogs = listByIds(ids);
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(min_timestampMills);
        scrollResult.setOffset(min_time_count);
        return Result.ok((scrollResult));
    }


    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }
}
