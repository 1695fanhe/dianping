package com.example.dianping.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dianping.dto.Result;
import com.example.dianping.entity.Shop;
import com.example.dianping.mapper.ShopMapper;
import com.example.dianping.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.dianping.utils.CacheClient;
import com.example.dianping.utils.RedisConstants;
import com.example.dianping.utils.RedisData;
import com.example.dianping.utils.SystemConstants;
import org.springframework.data.redis.connection.RedisGeoCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.dianping.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.example.dianping.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
//       Shop shop =queryWithPassThrough( id);
//        Shop shop = queryWithMutex( id);
//        Shop shop = queryWithLogicalExpire( id);
      Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        log.info("shop数据: {}", shop);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }


    /*public Shop queryWithMutex(Long id) {
        String shopJson= stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        if(shopJson != null){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if(shopJson != null)
        {
            return null;
        }
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            if(!isLock)
            {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            shop = getById(id);
//            模拟延时
//            Thread.sleep(200);
            if (shop == null)
            {
                stringRedisTemplate.opsForValue().set("cache:shop:" + id, "",2L, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unLock(lockKey);
        }
        return shop;
    }*/


    public void saveShopToRedis(Long id, Long expireSeconds){
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(redisData));
    }
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById( shop);
        stringRedisTemplate.delete("cache:shop:" + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if(x==null||y==null)
        {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
       int from=(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end=current*SystemConstants.DEFAULT_PAGE_SIZE;
        String key="shop:geo:"+typeId;
       GeoResults<RedisGeoCommands.GeoLocation<String>> results= stringRedisTemplate.opsForGeo().
                search(key, GeoReference.fromCoordinate(x,y),new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates()
                                .limit(end));
        if(results== null)
        {
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> contents =results.getContent();
        if(contents.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        Map<String,Distance> distanceMap=new HashMap<>(contents.size());
        List<Long> ids=new ArrayList<>(contents.size());
        contents.stream().skip(from).forEach(result -> {
           String shopIdstr=result.getContent().getName();
           ids.add(Long.valueOf(shopIdstr));
           Distance distance=result.getDistance();
           distanceMap.put(shopIdstr,distance);
        });
        String idstr=StrUtil.join(",",ids);
       List< Shop> shops= query().in("id",ids).last("ORDER BY FIELD(id,"+idstr+")").list();
       for(Shop shop:shops)
       {
           shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
       }
       return Result.ok(shops);
    }
}
