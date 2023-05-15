package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private static final Random random = new Random();
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
//        Shop result = (Shop) cacheClient.getData(CACHE_SHOP_KEY,id,Shop.class,CACHE_SHOP_TTL,TimeUnit.MINUTES,(id2)->{
//            return getById((Long)id2);
//        });
        //解决缓存击穿
        Shop result = (Shop) cacheClient.cacheBreakdownWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, CACHE_NULL_TTL, TimeUnit.SECONDS, (id2) -> {
            return getById((Long) id2);
        });
        if (result == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(result);
    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        String cacheKey;
        if (shop.getId() == null) {
            return Result.fail("商品错误...Cause by:商品Id不能为空 ");
        }
        cacheKey = CACHE_SHOP_KEY + shop.getId();
        //1.更新数据库
        updateById(shop);
        //2.删除缓存保证一致性
        stringRedisTemplate.delete(cacheKey);
        log.debug("缓存更新完成");
        return Result.ok();
    }

}
