package com.example.dianping;

import com.example.dianping.entity.Shop;
import com.example.dianping.service.impl.ShopServiceImpl;
import com.example.dianping.utils.CacheClient;
import com.example.dianping.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.example.dianping.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.example.dianping.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class DianpingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private ExecutorService es= Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() {
        CountDownLatch latch = new CountDownLatch(500);
        Runnable task = () -> {
            for (int i = 0; i < 500; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            es.submit(task);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long end = System.currentTimeMillis();
        System.out.println("耗时："+(end-start));
    }

    @Test
    public void testSaveShop() {
        Shop shop = shopService.getById(1L);
        cacheClient.set(CACHE_SHOP_KEY+1L, shop, 10L, TimeUnit.SECONDS);
    }
    @Test
    void loadShopData() {
        List< Shop> list = shopService.list();
        Map<Long, List< Shop>> map =list.stream().
                collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List< Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            List< Shop> value = entry.getValue();
            for(Shop shop:value)
            {
                stringRedisTemplate.opsForGeo().add(SHOP_GEO_KEY+typeId, new Point(shop.getX(), shop.getY()), shop.getId().toString());
            }
        }
    }

}
