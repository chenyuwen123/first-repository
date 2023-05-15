package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.jws.soap.SOAPBinding;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //手机号验证
    @Override
    public Result sendCode(String phone, HttpSession session) {
        /*
         * 1.提交手机号
         * 2.验证手机号
         * 3.生成验证码
         * 4.发送验证码
         * */
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不合法手机号
            log.debug("手机号码格式错误");
            Result.fail("手机号码格式错误");
        } else {
            //合法手机号
            //生成验证码
            String code = RandomUtil.randomNumbers(6);
            //保存到redis,加上业务前缀并设置验证码的失效期限为两分钟 // set key value ex 120
            log.debug("验证成功，验证码为:{}", code);
            stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
            //发送验证码至手机（待续）
        }
        return Result.ok();
    }

    //用户登录
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String tokenKey;
        String cacheCode;
        String code;
        UserDTO dto;
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //校验手机号
            log.debug("手机号码格式错误");
            return Result.fail("手机号码格式错误");
        }
        //校验验证码
        code = loginForm.getCode();
        if (code == null || !(cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone)).equals(code)) {
            return Result.fail("验证码错误");
        }
        //数据库内查询用户
        User user = query().eq("phone", phone).one();
        //用户是否存在？存入session：创建新用户存入session
        if (user == null) {
            user = createDefaultUserWithPhone(phone);
        }
        dto = BeanUtil.copyProperties(user, UserDTO.class);
        //将用户存入redis中
        //1.生成一个随机token，作为登录令牌
        String token = UUID.randomUUID().toString(false);
        //2.用redis.hash结构存储对象，token为key，对象hash为值
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(dto, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValie) -> fieldValie.toString()));
        tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, stringObjectMap);
        //3.参考session给token设置有效期
        stringRedisTemplate.expire(tokenKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回token给前端
        return Result.ok(token);
    }

    private User createDefaultUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomStringUpper(10));
        save(user);
        return user;
    }
}
