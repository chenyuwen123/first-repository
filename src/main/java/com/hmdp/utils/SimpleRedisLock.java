package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SimpleRedisLock implements ILock {
    private static final String KEY_PREFIX = "LOCK:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("luas\\unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private String keyname;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.keyname = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeout, TimeUnit unit) {
        //获取线程id
        String id = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + keyname, id, timeout, unit);
        return BooleanUtil.isTrue(aBoolean);
    }

    //    @Override
//    public void unLock() {
//        //获取线程标识
//        String id = ID_PREFIX+Thread.currentThread().getId();
//        //获取锁标识
//        String lockId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + keyname);
//        //判断是否一致
//        if (id.equals(lockId)) {
//            //释放锁
//            log.debug("解锁成功");
//            stringRedisTemplate.delete(KEY_PREFIX+keyname);
//        }else {
//            log.debug("解锁失败");
//        }
//    }
    //调用lua脚本保证操作原子性，作用与上述代码相同
    @Override
    public void unLock() {
        //调用脚本，在一条Java指令内所以能保证原子性？
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + keyname), ID_PREFIX + Thread.currentThread().getId());
    }
}
