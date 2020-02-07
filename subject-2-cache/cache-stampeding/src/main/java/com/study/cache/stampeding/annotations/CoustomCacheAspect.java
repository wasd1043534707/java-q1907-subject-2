package com.study.cache.stampeding.annotations;

import java.lang.reflect.Method;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import com.study.cache.stampeding.bloom.RedisBloomFilter;

@Component
@Aspect
public class CoustomCacheAspect {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
    
	@Resource(name = "mainRedisTemplate") 
	StringRedisTemplate mainRedisTemplate;
	
	@Autowired
	RedisBloomFilter filter;
	
    // 数据库限流，根据数据库连接数来定义大小
    Semaphore semaphore = new Semaphore(30);

    @Pointcut("@annotation(com.study.cache.stampeding.annotations.CoustomCache)")
    public void cachePointcut() {
    }

    // 定义相应的事件
    @Around("cachePointcut()")
    public Object doCache(ProceedingJoinPoint joinPoint) {
    	Object value = null;
    	
    	CoustomCache cacheAnnotation = findCoustomCache(joinPoint);
    	// 解析缓存Key
        String cacheKey = parseCacheKey(joinPoint);
        
        
        // 在缓存之前去进行过滤
        String bloomFilterName = cacheAnnotation.bloomFilterName();
    	boolean exists = filter.exists(bloomFilterName, cacheKey);
    	if(! exists) {
    		logger.warn(Thread.currentThread().getName()+" 您需要的商品是不存在的+++++++++++++++++++++++++++");
    		return "您需要的商品是不存在的";
    	}
        
        // 1、 判定缓存中是否存在
        value = mainRedisTemplate.opsForValue().get(cacheKey);
        if (value != null) {
        	logger.debug("从缓存中读取到值：" + value);
            return value;
        }
        
        // 访问数据库进行限流
        try {
        	
			if(semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
				
				value = mainRedisTemplate.opsForValue().get(cacheKey);
		        if (value != null) {
		        	logger.debug("从缓存中读取到值：" + value);
		            return value;
		        }
				
				// 交给服务层方法实现，从数据库获取
	            value = joinPoint.proceed();
				
				// 塞到缓存，过期时间10S
				final String v = value.toString();
				mainRedisTemplate.execute((RedisCallback<Boolean>) conn -> {
					return conn.setEx(cacheKey.getBytes(), 120, v.getBytes());
				});
			}else { // semaphore.tryAcquire(5, TimeUnit.SECONDS) 超时怎么办？
				// 再去获取一遍缓存，说不定已经有请求构建好了缓存。
				value = mainRedisTemplate.opsForValue().get(cacheKey);
				if(value != null) {
					logger.debug("等待后，再次从缓存获得");
					return value;
				}
    			
				// 缓存尚未构建好，进行服务降级，容错
    			// 友好的提示，对不起，票已售空、11.11 提示稍后付款；客官您慢些；
    			// 不断降低我们的预期目标, 外星人、小黑、华为、小米
				logger.debug("服务降级——容错处理");
			}
		
    	} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}finally {
    		try {
				semaphore.acquire();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
    	}
        return value;
    }
    
    private CoustomCache findCoustomCache(ProceedingJoinPoint joinPoint) {
		CoustomCache cacheAnnotation;
		try {
			MethodSignature signature = (MethodSignature) joinPoint.getSignature();
			Method method = joinPoint.getTarget().getClass().getMethod(signature.getName(), signature.getMethod().getParameterTypes());
			cacheAnnotation = method.getAnnotation(CoustomCache.class);
			return cacheAnnotation;
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		}
		return null;
    }
    
    
    /**
     * 获取缓存Key
     * @param joinPoint
     * @return
     */
    private String parseCacheKey(ProceedingJoinPoint joinPoint) {
    	CoustomCache cacheAnnotation;
		// 解析
    	String cacheKey = null;
		try {
			// 0-1、 当前方法上注解的内容
			MethodSignature signature = (MethodSignature) joinPoint.getSignature();
			Method method = joinPoint.getTarget().getClass().getMethod(signature.getName(), signature.getMethod().getParameterTypes());
			cacheAnnotation = findCoustomCache(joinPoint);
			String keyEl = cacheAnnotation.key();
			
			// 0-2、 前提条件：拿到作为key的依据  - 解析springEL表达式
			// 创建解析器
			ExpressionParser parser = new SpelExpressionParser();
			Expression expression = parser.parseExpression(keyEl);
			EvaluationContext context = new StandardEvaluationContext(); // 参数
			// 添加参数
			Object[] args = joinPoint.getArgs();
			DefaultParameterNameDiscoverer discover = new DefaultParameterNameDiscoverer();
			String[] parameterNames = discover.getParameterNames(method);
			for (int i = 0; i < parameterNames.length; i++) {
			    context.setVariable(parameterNames[i], args[i].toString());
			}
			String key = expression.getValue(context).toString();
			cacheKey = cacheAnnotation.prefix() == null ? "" : cacheAnnotation.prefix() + key;
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (EvaluationException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		}
        
        return cacheKey;
    }


}
