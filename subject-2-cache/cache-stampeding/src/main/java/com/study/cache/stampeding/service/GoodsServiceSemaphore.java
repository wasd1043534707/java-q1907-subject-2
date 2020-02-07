package com.study.cache.stampeding.service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.study.cache.stampeding.annotations.CoustomCache;
import com.study.cache.stampeding.bloom.RedisBloomFilter;

@Component
public class GoodsServiceSemaphore {

    private final Logger logger = LoggerFactory.getLogger(GoodsServiceSemaphore.class);

    @Resource(name = "mainRedisTemplate")
    StringRedisTemplate mainRedisTemplate;

    @Autowired
    DatabaseService databaseService;
    
    @Autowired
	RedisBloomFilter filter;
    
    // 数据库限流，根据数据库连接大小来定义
    Semaphore semaphore = new Semaphore(30);

    /**
     * 查询商品库存数
     *
     * @param goodsId 商品ID
     * @return 商品库存数
     */
    //@Cacheable //核心步骤，1/2/3
    public Object queryStock(final String goodsId) {
    	String cacheKey = "goodsStock-"+ goodsId;
    	
    	// 先通过布隆过滤器
    	boolean exists = filter.exists("goodsBloomFilter", cacheKey);
    	if(!exists) {
    		logger.warn(Thread.currentThread().getName()+" 您需要的商品不存在+++++++++++++++++++++++++++");
    		return "您需要的商品不存在";
    	}
    	
    	// 1. 先从Redis里面获取数据
    	String value = mainRedisTemplate.opsForValue().get(cacheKey);
    	
    	// 2. 缓存里面没有数据，从数据库取
    	if(value != null) {
    		logger.warn(Thread.currentThread().getName()+" 缓存中获得数据+++++++++++++++++++++++++++");
    		return value;
    	}
    	
    	// 去请求数据库，需要进行控制，根据连接数进行控制
    	try {
    		// 同一时间，只能有30个请求去数据库获取数据，并且重构缓存
    		// 170个请求在做什么？等待
			boolean acquire = semaphore.tryAcquire(5, TimeUnit.SECONDS);
			if(acquire) {
				// 再次从Redis里面获取数据
		    	value = mainRedisTemplate.opsForValue().get(cacheKey);
		    	if(value != null) {
		    		logger.warn(Thread.currentThread().getName()+" 缓存中获得数据+++++++++++++++++++++++++++");
		    		return value;
		    	}
		    	
		    	value = databaseService.queryFromDatabase(goodsId);
		    	System.err.println(Thread.currentThread().getName()+" 数据库中获得数据==============================");
				
				// 3. 塞到缓存，过期时间120S
				final String v = value;
				mainRedisTemplate.execute((RedisCallback<Boolean>) conn -> {
					return conn.setEx(cacheKey.getBytes(), 120, v.getBytes());
				});
			}else { // 如果等待5s，
				// 异常提示，容错机制
				// 客官您慢一点，手速太快、请稍后付款、
				// 外星人太火，没货了，小黑也不错哦，华为的设备、小米，不断降低期望值
				// 
				
			}
	    	
		} catch (InterruptedException e) {
			e.printStackTrace();
		}finally {
			semaphore.release();
		}
    	
        return value;
    }
    
    @CoustomCache(key = "#goodsId", bloomFilterName = "goodsBloomFilter", prefix = "goodsStock-")
    public String queryStockByCacheAnn(String goodsId) {
    	// 只关心业务代码，交给码农去干
    	String value = databaseService.queryFromDatabase(goodsId);
    	System.err.println(Thread.currentThread().getName()+" 数据库中获得数据==============================");
    	return value;
    }
}
