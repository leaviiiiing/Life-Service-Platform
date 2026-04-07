package com.hmdp;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@Slf4j
@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IShopService shopService;

    @Test
    void loadVoucherData() {
        // 假设你的秒杀优惠券 ID 是 10，库存 1000

        stringRedisTemplate.opsForValue().set("seckill:stock:10", "200");
    }

    @Test
    void testVoucherPreheat() {

        List<SeckillVoucher> list = seckillVoucherService.list();

        for (SeckillVoucher voucher : list) {

            String key = "seckill:stock:" + voucher.getVoucherId();

            stringRedisTemplate.opsForValue().set(key, voucher.getStock().toString());
        }

    }
    @Test
    void loadShopData(){
        //查询店铺信息
        List<Shop> list = shopService.list();
        //按typeId分组，id一致放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //分批写入redis GEOADD key 经度 纬度
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            for (Shop shop : shops) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString()
                        ,new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }

    }

    @Test
    void testHyperLogLog(){
        String[] values=new String[1000];
        int j=0;
        for(int i=0;i<1000000;i++){
            j = i % 1000;
            values[j]="user_"+ i;
            if (j == 999 ){
                stringRedisTemplate.opsForHyperLogLog().add("hyper_log_",values);
            }

        }
        Long hyperLog = stringRedisTemplate.opsForHyperLogLog().size("hyper_log_");
        System.out.println("count="+hyperLog);

    }

}
