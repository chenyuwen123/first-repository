package com.hmdp;

import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class HmDianPingApplicationTests {
    @Resource
    private ShopMapper mapper;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testWarm() throws InterruptedException {
        cacheClient.inputDataToRedis(CACHE_SHOP_KEY, 1L, CACHE_SHOP_TTL, TimeUnit.SECONDS, (id2) -> {
            return mapper.selectById((Long) id2);
        });
    }

    @Test
    public void testIdWorker() throws InterruptedException, ExecutionException {
//        Runnable task = ()->{
//            for (int i = 0; i < 100; i++) {
//                long worker = redisIdWorker.nextId("shop");
//                System.out.println("id="+worker);
//            }
//        };
//        for (int i = 0; i < 300; i++) {
//            es.submit(task);
//        }
        Callable<Void> task = () -> {
            RedisIdWorker redisIdWorker = new RedisIdWorker(stringRedisTemplate);
            for (int i = 0; i < 100; i++) {
                long worker = redisIdWorker.nextId("shop");
                System.out.println(Thread.currentThread().getName() + " id=" + worker);
            }
            return null;
        };
        ExecutorService es = Executors.newFixedThreadPool(10);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            futures.add(es.submit(task));
        }
        for (Future<Void> future : futures) {
            future.get();
        }
//        for (int i = 0; i < 100; i++) {
//            long worker = redisIdWorker.nextId("shop");
//            System.out.println("id="+worker);
//        }
    }
}
