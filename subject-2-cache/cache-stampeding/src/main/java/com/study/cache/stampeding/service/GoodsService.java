package com.study.cache.stampeding.service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.study.cache.stampeding.annotations.CoustomCache;
import com.study.cache.stampeding.bloom.RedisBloomFilter;

@Component
public class GoodsService {

    private final Logger logger = LoggerFactory.getLogger(GoodsService.class);

    @Resource(name = "mainRedisTemplate")
    StringRedisTemplate mainRedisTemplate;

    @Autowired
    DatabaseService databaseService;
    
    // 数据库限流，根据数据库的连接池大小进行设定
    Semaphore semaphore = new Semaphore(30);
    
    @Autowired
	RedisBloomFilter filter;

    /**
     * 查询商品库存数
     *
     * @param goodsId 商品ID
     * @return 商品库存数
     */
    @Cacheable //核心步骤， 1/2/3
    public Object queryStock(final String goodsId) {
    	// 1. 先从redis获取数据
    	String cacheKey = "goodsStock-"+goodsId;
    	
    	// 在缓存之前去进行过滤
    	boolean exists = filter.exists("goodsBloomFilter", cacheKey);
    	if(! exists) {
    		logger.warn(Thread.currentThread().getName()+" 您需要的商品是不存在的+++++++++++++++++++++++++++");
    		return "您需要的商品是不存在的";
    	}
    	
    	String value = mainRedisTemplate.opsForValue().get(cacheKey);
    	
    	// 2.缓存里面如果没有数据，从数据库获取
    	if(value != null) {
    		logger.warn(Thread.currentThread().getName()+" 缓存中获得数据+++++++++++++++++++++++++++");
    		return value;
    	}
    	
    	try {
    		// 300请求，
    		// 前30 请求去构建缓存，30个请求，后面30
			boolean acquire = semaphore.tryAcquire(5, TimeUnit.SECONDS);
			
			if(acquire) {
				
				// 去缓存再查一次，因为前面的请求已经构建好了缓存
				value = mainRedisTemplate.opsForValue().get(cacheKey);
				
				// 2.缓存里面如果没有数据，从数据库获取
				if(value != null) {
					logger.warn(Thread.currentThread().getName()+" 缓存中获得数据+++++++++++++++++++++++++++");
					return value;
				}
				
				// 数据操作
				value = databaseService.queryFromDatabase(goodsId);
				System.err.println(Thread.currentThread().getName()+" 数据库中获得数据==============================");
				
				// 3.更新Redis
				final String v = value;
				mainRedisTemplate.execute((RedisCallback<Boolean>) conn -> {
					return conn.setEx(cacheKey.getBytes(), 120, v.getBytes());
				});
			}else {		// 等待超时5s
				// 异常提示，容错机制
				// 客官您慢一点，手速太快、请稍后付款
				// 外星人太火，没有货，小黑也不错，华为、小米，不断地降低我们期望值
				// 返回一个固定值
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}finally {
			semaphore.release();
		}
    	
        return null;
    }
    
    @CoustomCache(key = "#goodsId", prefix = "goodsStock-", bloomFilterName = "goodsBloomFilter")
    public Object queryStockByAnn(final String goodsId) {
    	// CRUD，只需要关系业务代码，交给码农去做
    	return databaseService.queryFromDatabase(goodsId);
    }
}
