package com.study.cache.stampeding;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.study.cache.stampeding.bloom.RedisBloomFilter;

/**
 * BloomFilterTests
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class BloomFilterTests {
	@Autowired
	RedisBloomFilter filter;
	
	@Test
	public void test() {
		for(int i = 1; i<= 200; i++) {
			filter.addElement("goodsBloomFilter", "goodsStock-"+i);
			boolean result = filter.exists("goodsBloomFilter", "goodsStock-"+i);
			System.out.println("商品:"+i+"是否存在："+result);
		}
		
	}
}

