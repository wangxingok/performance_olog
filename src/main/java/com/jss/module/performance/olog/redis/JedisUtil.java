package com.jss.module.performance.olog.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisUtil {
	private static final Logger logger = LoggerFactory.getLogger(JedisUtil.class);
	private JedisPool jedisPool;

	public JedisUtil(String jedisIp, int jedisPort) {
		JedisPoolConfig config = new JedisPoolConfig();
		// config.setMaxActive(5000);
		config.setMaxIdle(256);// 20
		// config.setMaxWait(5000L);
		config.setTestOnBorrow(true);
		config.setTestOnReturn(true);
		config.setTestWhileIdle(true);
		config.setMinEvictableIdleTimeMillis(60000l);
		config.setTimeBetweenEvictionRunsMillis(3000l);
		config.setNumTestsPerEvictionRun(-1);
		int timeout = 60000;
		jedisPool = new JedisPool(config, jedisIp, jedisPort, timeout);

	}

	public void close() {
		jedisPool.close();
		logger.debug("Close jedis resource!");
	}

	public void lpush(String key, String value) {
		Jedis jedis = jedisPool.getResource();
		try {
			jedis.lpush(key.getBytes(), ObjectUtil.objectToBytes(value));
		} catch (Exception e) {
			logger.error("Met some error", e);
		} finally {
			jedisPool.returnResourceObject(jedis);
		}

	}

	public byte[] rpop(String key) {
		Jedis jedis = jedisPool.getResource();
		byte[] bytes = null;
		try {
			bytes = jedis.rpop(key.getBytes());
		} finally {
			jedisPool.returnResourceObject(jedis);
		}
		return bytes;

	}

}
