package com.study.cache.stampeding.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 从数据库中查询
 * 
 */
@Component
public class DatabaseService {

	@Autowired
	JdbcTemplate jdbcTemplate;

	public String queryFromDatabase(String goodsId) {
		String sql = "SELECT quantity FROM goods WHERE id =  '" + goodsId + "'";

		Map<String, Object> result = jdbcTemplate.queryForMap(sql);

		return result.get("quantity").toString();
	}
}
