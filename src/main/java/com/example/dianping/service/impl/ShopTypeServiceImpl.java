package com.example.dianping.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.dianping.dto.Result;
import com.example.dianping.entity.ShopType;
import com.example.dianping.mapper.ShopTypeMapper;
import com.example.dianping.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// ... existing code ...

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = "cache:shop:type";

        // 1. 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            List<ShopType> list = JSONUtil.toList(JSONUtil.parseArray(json), ShopType.class);
            return Result.ok(list);
        }

        // 2. 查数据库
        List<ShopType> typeList = this.query().orderByAsc("sort").list();

        // 3. 空值处理
        if (typeList == null || typeList.isEmpty()) {
            stringRedisTemplate.opsForValue().set(key, "", 60, TimeUnit.SECONDS);
            return Result.fail("没有数据");
        }

        // 4. 写入缓存
        stringRedisTemplate.opsForValue().set(
                key,
                JSONUtil.toJsonStr(typeList),
                3600,
                TimeUnit.SECONDS
        );

        return Result.ok(typeList);
    }
}
