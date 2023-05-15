package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断时间是否符合要求
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已结束");
        }
        //3.判断库存是否符合要求
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        /*基于redis的分布式锁*/
        //创建锁对象
        //传入的name则作为锁对象，如果只传入一个业务名的话则锁住的是一整个业务，如果传入业务名+用户id则锁住当前用户的业务
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //获取锁
        RLock lock = redissonClient.getLock("order:" + userId);
        boolean islock = lock.tryLock(1, 10, TimeUnit.SECONDS);

        if (!islock) {
            //获取锁失败，返回失败信息
            return Result.fail("不允许重复下单");
        }
        //获取锁成功
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        } finally {
            lock.unlock();
        }

        /*基于sychorized的锁*/
//        synchronized(userId.toString().intern()){
//            //获得跟事务有关的代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucher(voucherId);
//        }
    }

    @Transactional
    public Result createVoucher(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        //根据券id和用户id查询是否券表里有这样一张券
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("已经下单过了哦！");
        }
        //4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .gt("stock", 0)
                .eq("voucher_id", voucherId).update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //5.2 用户id
        voucherOrder.setUserId(userId);
        //5.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //6.返回订单ID
        return Result.ok(orderId);
    }
}
