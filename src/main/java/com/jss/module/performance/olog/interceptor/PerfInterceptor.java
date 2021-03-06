package com.jss.module.performance.olog.interceptor;

import com.jss.module.performance.olog.domain.MethodStats;
import com.jss.module.performance.olog.kafka.KafkaProducer;
import com.jss.module.performance.olog.redis.JedisUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于AOP拦截指定类型的方法，基于SLF4J输出类包、方法名及方法执行时间
 * 
 * @author junsansi
 *
 */
public class PerfInterceptor implements MethodInterceptor {

	/**
	 * performance.log.open 指定拦截器状态 1表示启用，0表示禁用 当不指定时，默认为启用状态
	 */
	@Value("${performance.log.open:1}") // 默认为启用状态
	private long isOpen;

	/**
	 * performance.log.frequency 指定对方法的拦截频率，应为大于0的正整数 默认值为1,表示每次均会拦截
	 */
	@Value("${performance.log.frequency:1}") // 默认为每次均进行拦截
	private long frequency;

	/**
	 * performance.log.longquerytime 指定方法执行时间将做为慢查询方法的时间阀值，以ms为单位，默认是500ms
	 */
	@Value("${performance.log.longquerytime:50}")
	private long longquerytime;

	/**
	 * performance.log.destination
	 * 指定日志文件输出路径，目前支持logfile/redis/kafka等数种，默认为logfile
	 */
	@Value("${performance.log.destination:logfile}")
	private String fileDest;

	/**
	 * 指定kafka的主题
	 */
	@Value("${performance.log.kafka.topic}")
	private String kafkatopic;

	/**
	 * 指定kafka的连接地址，当broker为空时，destination默认值为logfile
	 */
	@Value("${performance.log.kafka.broker}")
	private String kafkabroker;

	/**
	 * 指定redis的连接地址
	 */
	@Value("${performance.log.redis.host}")
	private String redisHost;
	
	/**
	 * 指定redis的连接端口
	 */
	@Value("${performance.log.redis.port}")
	private int redisPort;

	//redis 工具类
	private JedisUtil jedisUtil;

	// kafka 生成者
	private KafkaProducer kafkaProducer;

	//获取 redis 工具类实例
	private JedisUtil getJedisUtil(){
		if(jedisUtil==null) {
			jedisUtil = new JedisUtil(redisHost, redisPort);
		}
		return jedisUtil;
	}

	private KafkaProducer getKafkaProducer(){
		if(kafkaProducer == null) {
			kafkaProducer = new KafkaProducer(kafkatopic, kafkabroker);
		}
		return kafkaProducer;
	}
	
	/**
	 * 被调用方法缓存区
	 */
	private static ConcurrentHashMap<String, MethodStats> methodStats = new ConcurrentHashMap<String, MethodStats>();

	private static final Logger logger = LoggerFactory.getLogger(PerfInterceptor.class);

	public Object invoke(MethodInvocation method) throws Throwable {
		long methodStarttime = System.currentTimeMillis();
		
		try {
			return method.proceed();
		} finally {
			if (isOpen == 1) {
				updateStats(method.getMethod().getDeclaringClass().getName(), method.getMethod().getName(), methodStarttime);
			}
		}
	}

	private void updateStats(String methodClass, String methodName, long methodStarttime) {
		long methodElapsedTime = System.currentTimeMillis() - methodStarttime;
		String methodFullname = methodClass + "." + methodName;
		MethodStats stats = methodStats.get(methodFullname);
		if (stats == null) {
			stats = new MethodStats(methodFullname);
			stats.setExecutedNum(1);
			stats.setMethodElapsedTime(methodElapsedTime);
			stats.setMaxTime(methodElapsedTime);
			stats.setTotalTime(methodElapsedTime);
			stats.setMethodName(methodName);
			
			methodStats.put(methodFullname, stats);
		}else{
			stats.setExecutedNum(stats.getExecutedNum()+1);
			if (stats.getMaxTime() < methodElapsedTime) stats.setMaxTime(methodElapsedTime);
			stats.setMethodElapsedTime(methodElapsedTime);
			stats.setTotalTime(stats.getTotalTime()+methodElapsedTime);
		}
		
		String message = stats.toString();

		if (!StringUtils.isNotBlank(kafkabroker) && !StringUtils.isNotBlank(redisHost) ) {
			fileDest = "logfile";
		}
		// 检查执行频率条件是否满足
		if (stats.getExecutedNum() % frequency == 0) {

			// 检查执行时间条件是否满足
			if (methodElapsedTime >= longquerytime) {

				switch (fileDest) {
				// 输出到kafka
				case "kafka":
					KafkaProducer kp = this.getKafkaProducer();
					kp.sendMsg(message);
					break;
				// 输出到redis
				case "redis":
					JedisUtil jedis = this.getJedisUtil();
					jedis.lpush("perfolog", message);
					break;
				// 默认输出到日志文件
				default:
					logger.info(message);
					break;
				}

			}

		}
		//拦截器的执行时间
		/*
		long interceptorElapsedTime = System.currentTimeMillis() - methodStarttime - methodElapsedTime;
		stats.setInterceptorElapsedTime(interceptorElapsedTime);
		logger.info(stats.toString()+"  interceptorExecuted time: "+stats.getInterceptorElapsedTime()+" ms.");
		*/
	}

	/**
	 * spring 销毁时调用的方法
	 */
	public void destroy(){
		try {
			this.getJedisUtil().close();
		} catch (Exception ex){
			logger.error("destroy jedis error", ex);
		}
		try {
			this.getKafkaProducer().close();
		} catch (Exception ex) {
			logger.error("destroy kafka producer error", ex);
		}
	}
}