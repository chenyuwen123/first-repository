package com.hmdp.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPES_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopTypeMapper mapper;

    @SneakyThrows
    @Override
    public Result queryAll() {

        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOPTYPES_KEY);
        log.debug("得出的数据为：{}", JSONUtil.toJsonStr(shopJson));
//        //2.判断缓存是否命中
        if (!StrUtil.isBlank(shopJson)) {
            //3.缓存命中返回数据

            log.debug("缓存命中，取出的Json数据为{}", JSONUtil.toJsonStr(shopJson));
            return Result.ok(JSONUtil.parseArray(shopJson));
        }

        //4.缓存未命中从数据库内查询数据
        QueryWrapper<ShopType> queryWrapper = new QueryWrapper<>();
        List<ShopType> shopTypes = mapper.selectList(queryWrapper);
        //5.数据库内没有数据则返回404错误代码
        if (shopTypes == null) {
            log.debug("缓存未命中，且数据库查询不到ShopTypes数据");
            return Result.fail("店铺类型不存在");
        }
        log.debug("缓存未命中，数据库查询到shopTypes数据,取出的对象为{}", JSONUtil.toJsonStr(shopTypes));
        //6.数据库内有数据则将数据写入redis缓存中并将数据返回给客户端
        stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPES_KEY, JSONUtil.toJsonStr(shopTypes));
        return Result.ok(JSONUtil.toJsonStr(shopTypes));
    }
}
