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
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendcode(String phone, HttpSession session) {

        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //验证错误
            return Result.fail("手机号格式错误");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);

        //存到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.debug("发送验证码:{}", code);


        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //验证错误
            return Result.fail("手机号格式错误");
        }
        //从redis中校验验证码
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cachecode ==null||!cachecode.equals(code)){

            return Result.fail("验证码错误");
        }
        //查询用户并判断是否存在
        User user = lambdaQuery().eq(User::getPhone, phone).one();
        if(user==null){
            user=creatUserWithPhone(phone);
        }
        //生成token
        String token = UUID.randomUUID().toString(true);
        //将User转HashMap并存储到redis
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);

        Map<String,Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldvalue)->fieldvalue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);

        //返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {

        //获取当前用户
        String userId = UserHolder.getUser().getId().toString();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId +":"+ date;
        //判断今天是本月的第几天
        int offset = now.getDayOfMonth();
        //写入redis   SETBIT key 1
        Boolean bit = stringRedisTemplate.opsForValue().getBit(key, offset - 1);
        if(Boolean.TRUE.equals(bit)){
            return Result.fail("已登录");
        }
        stringRedisTemplate.opsForValue().setBit(key, offset-1, true);

        return Result.ok();
    }

    @Override
    public Result signCount() {

        //获取当前用户
        String userId = UserHolder.getUser().getId().toString();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId +":"+ date;
        //判断今天是本月的第几天
        int offset = now.getDayOfMonth();
        //获取截至今日的签到记录 返回值是十进制 BITFIELD key GET offset 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(offset))
                        .valueAt(0));
        if(result==null || result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null||num==0) {
            return Result.ok(0);
        }
        //遍历
        int count = 0;
        //最后一位为0 未签到
        //最后一位为1 已签到 计数器+1
        //让数字与1做与运算，得到数字的最后一位bit位 判断该bit位是否为1
        while ((num & 1) == 1) {

            count++;
            //数字向右移一位
            num >>>= 1;

        }
        return Result.ok(count);
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;

    }
}
