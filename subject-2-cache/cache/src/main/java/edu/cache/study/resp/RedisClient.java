package edu.cache.study.resp;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import redis.clients.jedis.Jedis;

public class RedisClient {
	Socket connection;
	public RedisClient(String host, int port) throws UnknownHostException, IOException {
		connection = new Socket(host, port);
	}
	
	public String set (String key, String value) throws IOException {
		// set key value
		// 构建请求数据包
		StringBuffer command = new StringBuffer();
		// 第一个部分，描述整个数据包的参数情况
		command.append("*3").append("\r\n");
		
		// 参数第一个部分, set
		command.append("$3").append("\r\n");
		command.append("set").append("\r\n");
		
		// 参数第二个部分, key
		command.append("$").append(key.getBytes().length).append("\r\n");
		command.append(key).append("\r\n");
		
		// 参数第三个部分, value
		command.append("$").append(value.getBytes().length).append("\r\n");
		command.append(value).append("\r\n");
		
		System.out.println(command.toString());
		
		connection.getOutputStream().write(command.toString().getBytes());
		
		byte[] repsonse = new byte[1024];
		connection.getInputStream().read(repsonse);
		return new String(repsonse); 
	}
	
	
	// get 命令实现，更简单了。作为课堂作业留给同学自己实现。
	// 只有两个部分 get key

	public static void main(String[] args) throws UnknownHostException, IOException {
		//Jedis jedis = new Jedis();
		RedisClient jedis = new RedisClient("127.0.0.1", 6379);
		try {
			jedis.set("hello", "code666666");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
