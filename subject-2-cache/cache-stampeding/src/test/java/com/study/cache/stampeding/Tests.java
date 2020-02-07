package com.study.cache.stampeding;

import java.util.Random;

public class Tests {
	
	public static void main(String[] args) {
		String[] goodsNames = new String[] {"apple","LV女士包","IPhone9","蝉丝被","外星人","拉杆箱","扫地机器人","中秋月饼"};
		Random random = new Random();
		
		for(int i = 1; i < 200; i++) {
			String goodsName = goodsNames[random.nextInt(goodsNames.length)]+"-"+i;
			System.out.println("INSERT INTO goods (id, quantity, name, price)"
					+" VALUES ("+i+", "+2000+", '"+goodsName+"', '289.9');");
		}
	}
}
