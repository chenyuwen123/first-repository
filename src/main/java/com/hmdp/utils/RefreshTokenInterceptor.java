package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

//刷新token拦截器
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //前置拦截
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tokenKey;
        //获取请求头token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }

        tokenKey = LOGIN_USER_KEY + token;
        //基于token从redis中取出用户
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(tokenKey);
        //拦截空user
        if (userMap.isEmpty()) {
            return true;
        }
        //将查询到的redis Hash对象转换成UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //将非空user保存到ThreadLocal中
        UserHolder.saveUser(userDTO);
        //刷新token有效期
        log.debug("刷新token拦截器，tokenKey为{}", tokenKey);
        redisTemplate.expire(tokenKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }

    //后置拦截
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
