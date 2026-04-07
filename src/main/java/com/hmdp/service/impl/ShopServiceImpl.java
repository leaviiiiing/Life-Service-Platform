package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CasheClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.DEFAULT_PAGE_SIZE;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CasheClient casheClient;

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        Shop shop=casheClient
                .queryWithPassthrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = casheClient.queryWithMutex(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在!");
        }

        return Result.ok(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class) 
    public Result update(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);

        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否有坐标
        if (x==null||y==null) {
            //不需要坐标，直接查询数据库
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            return Result.ok(page);
        }
        //计算分页参数
        int from = (current-1)*DEFAULT_PAGE_SIZE;
        int end = current*DEFAULT_PAGE_SIZE;
        //查询redis 按距离排序、分页 shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo()
                .search(key
                        , GeoReference.fromCoordinate(x, y)
                        , new Distance(500000000)
                        , RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        //解析出id
        if (search == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();
        if (content.size()<=from){
            return Result.ok(Collections.emptyList());
        }
        //截取
        List<Long> shopIds = new ArrayList<>(content.size());
        Map<String, Distance> distanceMap = new HashMap<>(content.size());
         content.stream().skip(from).forEach(result -> {
             String shopIdStr= result.getContent().getName();
             shopIds.add(Long.valueOf(shopIdStr));
             Distance distance = result.getDistance();
             distanceMap.put(shopIdStr, distance);
         });
        //根据id查询店铺
        String join = StrUtil.join(",", shopIds);
        List<Shop> shops = query()
                .in("id", shopIds)
                .last("ORDER BY FIELD(id," + join + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }

    /*    public Shop queryWithMutex(Long id){
        //根据id查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果存在,直接返回
        if (!StrUtil.isBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断是否为空
        if (shopJson!=null) {
            return null;
        }
        //获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop=null;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否取锁成功
            if (!isLock) {
                //失败：休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功：查数据库
            shop = getById(id);
            //如果不在数据库中
            if (shop == null) {
                //将空值写入缓存
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //如果存在，保存到缓存
            stringRedisTemplate.opsForValue().set(
                    CACHE_SHOP_KEY + id,
                    JSONUtil.toJsonStr(shop),
                    CACHE_SHOP_TTL,
                    TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unlock(lockKey);
        }

        return shop;
    }

    public Shop queryWithPassthrough(Long id) {
        //根据id查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果存在,直接返回
        if (!StrUtil.isBlank(shopJson)) {
            Shop shop =  JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否为空
        if (shopJson!=null) {
            return null;
        }
        //如果不存在,查询数据库
        Shop shop = getById(id);
        //如果不在数据库中
        if (shop == null) {
            //将空值写入缓存
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //如果存在，保存到缓存，并返回
        stringRedisTemplate.opsForValue().set(
                CACHE_SHOP_KEY + id,
                JSONUtil.toJsonStr(shop),
                CACHE_SHOP_TTL,
                TimeUnit.MINUTES);
        return shop;
    }

    private boolean tryLock(String key) {
        boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }*/
}
