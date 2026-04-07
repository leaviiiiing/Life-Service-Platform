package com.hmdp.config.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class TokenRefreshIntercertor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public TokenRefreshIntercertor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //基于请求头中的token查询User
        String token = request.getHeader("authorization");

        if(StrUtil.isBlank(token)){
            return true;
        }
        Map<Object,Object> usermap =stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);
        //判断用户是否为空
        if(usermap.isEmpty()){
            return true;
        }
        //将Map转成UserDTO
        UserDTO userDTO= BeanUtil.fillBeanWithMap(usermap, new UserDTO(),false);

        //将UserDTo存到ThreadLocal
        UserHolder.saveUser(userDTO);
        //刷新token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }

}
