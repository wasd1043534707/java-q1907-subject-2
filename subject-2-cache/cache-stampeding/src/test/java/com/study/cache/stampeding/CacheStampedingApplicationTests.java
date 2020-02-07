package com.study.cache.stampeding;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.study.cache.stampeding.service.GoodsService;
import com.study.cache.stampeding.service.GoodsServiceSemaphore;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CacheStampedingApplicationTests {

    long timed = 0L;

    @Before
    public void start() {
        System.out.println("开始测试");
        timed = System.currentTimeMillis();
    }

    @After
    public void end() {
        System.out.println("结束测试,执行时长：" + (System.currentTimeMillis() - timed));
    }

    @Autowired
    GoodsService service;

    // 商品
    private static final String Goods_ID = "195";

    // 模拟的请求数量
    private static final int THREAD_NUM = 200;

    // 倒计数器 juc包中常用工具类
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    @Test
    public void benchmark() throws InterruptedException {
        // 创建 并不是马上发起请求
        Thread[] threads = new Thread[THREAD_NUM];
        Random random = new Random();
        
        for (int i = 0; i < THREAD_NUM; i++) {
            // 多线程模拟用户查询请求
            Thread thread = new Thread(() -> {
                try {
                	String gId = Goods_ID;
                	int randomGoodsId = ThreadLocalRandom.current().nextInt(150, 350);
                	gId = String.valueOf(randomGoodsId);
                    // 代码在这里等待，等待countDownLatch为0，代表所有线程都start，再运行后续的代码
                    countDownLatch.await();
                    // http请求，实际上就是多线程调用这个方法
                    service.queryStockByAnn(gId);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            threads[i] = thread;

            thread.start();
        }
        
        // 田径。启动后，倒计时器倒计数 减一，代表又有一个线程就绪了
        countDownLatch.countDown();

        // 等待上面所有线程执行完毕之后，结束测试
        for (Thread thread : threads) {
            thread.join();
        }
    }

}
