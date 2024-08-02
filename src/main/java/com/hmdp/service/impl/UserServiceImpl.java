package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
//import java.util.UUID;
//import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        //检验手机号
//        if(RegexUtils.isPhoneInvalid(phone)){
//            //不符合,返回错误信息
//            return Result.fail("手机号码格式有误");
//        }
//        //符合，生成验证按
//        String code = RandomUtil.randomNumbers(6);
//        //session保存验证码
//        session.setAttribute("code",code);
//        log.debug("发送验证码成功,验证码为：{}",code);
//        //返回成功信息
//        return Result.ok();

        //使用redis代替session存储登录数据
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)) {
//
//          不符合返回错误信息
            return Result.fail("手机格式错误！");
        }
//        符合生成验证码
        String code = RandomUtil.randomNumbers(6);
//        保存验证码到redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

//        发送验证码
        log.debug("发送短信验证码成功,验证码:{}",code);
//                log.debug("发送验证码成功,验证码为：{}",code);
//        返回ok
        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        String phone = loginForm.getPhone();
//        //检验售后机号
//        if (RegexUtils.isPhoneInvalid(phone)){
////            不符合,返回错误信息
//            return Result.fail("手机格式有误");
//
//        }
//        //检验验证码
//        Object sessionCode = session.getAttribute("code");
//        String code = loginForm.getCode();
//        if(sessionCode == null || !sessionCode.toString().equals(code)){
//            return Result.fail("验证码失效或有误");
//        }
//
////        手机号查看用户是否存在
//        User user = query().eq("phone",phone).one();
//
//        if(user ==null){
////            不存在创建用户
//            user = createUserWithPhone(phone);
//        }
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
//
//        return Result.ok();

        //使用redis代替session存储
        //校验手机号
        String phone = loginForm.getPhone();
//
        if (RegexUtils.isPhoneInvalid(phone)){
            //不符合,返回错误信息
            return Result.fail("手机格式错误！");

        }
//        校验验证码
        String cacheCode =  stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if (cacheCode==null||!cacheCode.equals(code)) {
//
//        不一致报错
            return Result.fail("验证码错误！");
        }
//        一致 根据手机号查询用户
        User user = query().eq("phone",phone).one();

//        判断用户是否存在
        if(user ==null){
            //        不存在创建新用户并保存
            user = createUserWithPhone(phone);
        }



//        保存用户新信息到redis中
//        随机生产token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
//        String token = UUID.randomUUID().toString(true);
//        将User对象转换为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String,Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY+token;
//        存储
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
//        设置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);


    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }

}
