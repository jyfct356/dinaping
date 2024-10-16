package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;

import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Random;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private IUserInfoService userInfoService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        // 手机号的校验
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 不符合 返回
            return Result.fail("手机号格式有误！");
        }
        // 生成验证码
        String validateCode = RandomUtil.randomString(6);
        // 保存验证码到session
//        session.setAttribute("validateCode", validateCode);
        // 保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, validateCode);
        // 发送验证码
        log.info("验证码已发送：{}", validateCode);
        System.out.println("发送验证码：" + validateCode);
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        log.info("退出登录.");
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(){
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result getUserById(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.fail("用户不存在.");
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}
