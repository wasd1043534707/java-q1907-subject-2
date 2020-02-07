package com.study.consistence.hash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistenceHash {
	// 物理节点集合
	private List<String> realNodes = new ArrayList<String>();
	
	// 虚拟节点数，用户进行指定
	private int virutalNums = 100;
	
	// 物理节点与虚拟节点的对应关系，key物理节点，value存储的是虚拟hash值
	private Map<String, List<Integer>> real2VirturalMap = new HashMap<String, List<Integer>>();
	
	// 排序存储结构，红黑树，key存储虚拟节点hash值，value物理节点
	private SortedMap<Integer, String> sortedMap = new TreeMap<Integer, String>();

	public ConsistenceHash() {}
	public ConsistenceHash(int virutalNums) {
		this.virutalNums = virutalNums;
	}
	
	// 添加服务节点
	public void addServer(String node) {
		realNodes.add(node);
		String vnode = null;
		
		// i:v-001 , count,产生了虚拟节点的次数
		int i = 0, count = 0;
		
		List<Integer> vritualNodes = new ArrayList<Integer>();
		real2VirturalMap.put(node, vritualNodes);
		
		// 创建虚拟节点，并且放到圆环上去（排序存储）
		while(count < this.virutalNums) {
			i++;
			vnode = node + "V-" +i;
			int hashValue = FNV1_32_HASH.getHash(vnode);
			
			// 解决hash碰撞
			if(! sortedMap.containsKey(hashValue)) {
				vritualNodes.add(hashValue);
				sortedMap.put(hashValue, node);
				count ++;
			}
		}
	}
	
	// 移除服务器
	public void removeServer(String node) {
		List<Integer> vritualNodes = real2VirturalMap.get(node);
		if(vritualNodes != null) {
			for(Integer hashVal : vritualNodes) {
				sortedMap.remove(hashVal);
			}
		}
		real2VirturalMap.remove(node);
		realNodes.remove(node);
	}
	
	// 根据数据的存放点，根据key查找物理节点
	public String getServer(String key) {
		// 根据key产生hash值
		int hashValue = FNV1_32_HASH.getHash(key);
		SortedMap<Integer, String> subMap = sortedMap.tailMap(hashValue);
		if(subMap == null || subMap.isEmpty()) {
			return sortedMap.get(sortedMap.firstKey());
		}else {
			return subMap.get(subMap.firstKey());
		}
	}
	
	
	public static void main(String[] args) {
		ConsistenceHash ch = new ConsistenceHash();
		ch.addServer("192.168.1.10");
		ch.addServer("192.168.1.11");
		ch.addServer("192.168.1.12");

		for (int i = 0; i < 10; i++) {
			System.out.println("a" + i + " 对应的服务器是：" + ch.getServer("a" + i));
		}
	}
	
}
