package com.study.cache.stampeding.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义的缓存注解
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoustomCache {
    /**
     * key的规则，可以使用springEL表达式，可以使用方法执行的一些参数
     */
    String key();
    
    /**
     * 缓存key的前缀
     * @return
     */
    String prefix();
    
    /**
     * 采用布隆过滤器的名称
     * @return
     */
    String bloomFilterName();
}
