package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CasheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public  CasheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long expireTime, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long expireTime, TimeUnit timeUnit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(JSONUtil.toJsonStr(value));
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassthrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFunction, Long expireTime, TimeUnit timeUnit) {
        //根据id查询缓存
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        //如果存在,直接返回
        if (!StrUtil.isBlank(json)) {
            return  JSONUtil.toBean(json, type);
        }
        //判断是否为空
        if (json!=null) {
            return null;
        }
        //如果不存在,查询数据库
        R r = dbFunction.apply(id);
        //如果不在数据库中
        if (r == null) {
            //将空值写入缓存
            stringRedisTemplate.opsForValue().set(keyPrefix + id, JSONUtil.toJsonStr(r),CACHE_NULL_TTL,timeUnit);
            return null;
        }
        //如果存在，保存到缓存，并返回
        this.set(keyPrefix + id, r, expireTime, timeUnit);

        return r;
    }


    public <R,ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFunction, Long expireTime, TimeUnit timeUnit){
        //根据id查询缓存
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        //如果为空,直接返回
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);
        R r =JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expiretime = redisData.getExpireTime();
        //判断是否过期
        if (expiretime.isAfter(LocalDateTime.now())) {
            //未过期
            return r;
        }
        //已过期，重建缓存
        //获取互斥锁
        String lockKey = LOCK_KEY + id;
        //判断是否获取成功
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            //成功，开启独立线程，重建缓存
            CACHE_REBILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R r1 = dbFunction.apply(id);
                    //写入缓存
                    this.setWithLogicalExpire(keyPrefix + id, r1, expireTime, timeUnit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }

        return r;
    }

    private boolean tryLock(String key) {
        boolean flag = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS));
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    private  static final ExecutorService CACHE_REBILD_EXECUTOR = Executors.newFixedThreadPool(10);

}
