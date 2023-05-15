package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient<T> {
    private static final ExecutorService CACHE_REBUID_EXECUTOR = Executors.newSingleThreadExecutor();
    private final StringRedisTemplate redisTemplate;

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //1.将任意对象序列化为JSON并存储在redis中，并且可以设置TTL过期时间
    public void setDataTTL(String key, Object value, Long time, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //2.将任意对象序列化为JSON并存储在redis中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public <ID> void setDataWithLogicTimeout(String prefix, ID id, Object data, Long time, TimeUnit unit) {
        String cacheKey = prefix + id;
        RedisData redisData = new RedisData();
        redisData.setData(data);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //3.写入redis缓存
        redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(redisData));
    }

    //3.将从redis中查询到的JSON反序列化为指定类型的对象，并且可以缓存空值用以防止缓存穿透问题
    public <T, ID> T getData(String keyprefix, ID id, Class<T> type, Long time, TimeUnit unit, Function<ID, T> dbFallback) {
        T data;
        String key = keyprefix + id;
        String json = redisTemplate.opsForValue().get(key);
        //差redis看是否有真数据
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        //需要查表的时候让调用者传入查表方法
        data = dbFallback.apply(id);
        //判断查表结果是否为null
        if (data == null) {
            //为null则写入空值到redis防止缓存穿透
            redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //不为空则写入真实数据
        this.setDataTTL(key, data, time, unit);
        return data;
    }
    //4.将查询到的JSON反序列化为指定类型对象，并且可以基于逻辑过期时间来防止缓存击穿

    //锁
    private boolean tryLock(String id) {
        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unLock(String id) {
        redisTemplate.delete(LOCK_SHOP_KEY + id);
    }

    //解决缓存击穿的查询方案（基于逻辑过期）
    public <T, ID> T cacheBreakdownWithLogicalExpire(String prefix, ID id, Class<T> type, Long time, TimeUnit unit, Function<ID, T> dbFallback) {
        RedisData redisData;
        T data;
        String key = prefix + id;
        String lockKey = LOCK_SHOP_KEY + id;
        //1.从redis中查询id的shop数据
        String shopJson = redisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if (StrUtil.isBlank(shopJson)) {
            //缓存未命中返回空
            log.debug("缓存未命中，返回空");
            return null;
        }
        //3.缓存命中
        redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //判断是否逻辑过期
        //假设逻辑未过期为false

        data = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        log.debug("expireTime为{},LocalDateTime.now为{},是否未过期{}", expireTime, LocalDateTime.now(), expireTime.isAfter(LocalDateTime.now()));
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期直接返回
            log.debug("缓存命中且未过期");
            return data;
        }
        //尝试缓存重建
        boolean isLock = tryLock(lockKey);
        //获取互斥锁成功则新启动一个线程实现缓存重建
        if (isLock) {
            log.debug("获取互斥锁成功并重建缓存");
            CACHE_REBUID_EXECUTOR.execute(() -> {
                try {
                    //重建缓存
                    //1.查表
//                    T apply = dbFallback.apply(id);
                    //2.写入缓存
                    this.inputDataToRedis(CACHE_SHOP_KEY, id, time, unit, dbFallback);
                } catch (Exception e) {
                    throw new RuntimeException();
                } finally {
                    log.debug("释放互斥锁");
                    unLock(lockKey);
                }
            });
        }
        log.debug("返回数据");
        //无论是否获取互斥锁成功都返回旧数据
        return data;
    }

    //缓存预热
    public <T, ID> void inputDataToRedis(String prefix, ID id, Long time, TimeUnit unit, Function<ID, T> dbFallback) throws InterruptedException {
        //1.查询店铺信息
        T data = dbFallback.apply(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(data);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //3.写入redis缓存
        redisTemplate.opsForValue().set(prefix + id, JSONUtil.toJsonStr(redisData));
        log.debug("将类型为{}的数据写入到缓存中", data.getClass());
    }
}
