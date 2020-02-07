package edu.cache.study.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.alibaba.otter.canal.protocol.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;

public class CanalClientTest {
    static Jedis jedis;

    public static void main(String args[]) {
        // TODO 以用户信息更新为例。（不同业务场景 都是有区别的）
        // jedis
        jedis = new Jedis();
        // 和canal服务器进行连接
        CanalConnector connector = CanalConnectors.
        		newSingleConnector(new InetSocketAddress("192.168.100.13",
                11111), "example", "canal", "canal");
        int batchSize = 1000;
        try {
            connector.connect(); // 连接
            connector.subscribe(".*\\..*");
            connector.rollback();
            while (true) {
                Message message = connector.getWithoutAck(batchSize); // 获取指定数量的数据-- binlog 数据库变动记录
                long batchId = message.getId();
                int size = message.getEntries().size();
                if (batchId == -1 || size == 0) {
                	// System.out.println("empty count ");
                    try {
                        Thread.sleep(1000); // 如果这一秒没数据，那就等待一秒钟
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    processEntry(message.getEntries()); // 处理binlog-- 数据发生了变化，修改对应的缓存
                }
                connector.ack(batchId); // 提交确认
                // connector.rollback(batchId); // 处理失败, 回滚数据
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } finally {
            connector.disconnect();
        }
    }

    private static void processEntry(List<Entry> entrys) throws JsonProcessingException {
        for (Entry entry : entrys) {
            if (entry.getEntryType() == EntryType.TRANSACTIONBEGIN || entry.getEntryType() == EntryType.TRANSACTIONEND) {
                continue;
            }
            RowChange rowChage = null;
            try {
                rowChage = RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("ERROR ## parser of eromanga-event has an error , data:" + entry.toString(),
                        e);
            }
            EventType eventType = rowChage.getEventType();
            System.out.println(String.format("================> binlog[%s:%s] , name[%s,%s] , eventType : %s",
                    entry.getHeader().getLogfileName(), entry.getHeader().getLogfileOffset(),
                    entry.getHeader().getSchemaName(), entry.getHeader().getTableName(),
                    eventType));

            for (RowData rowData : rowChage.getRowDatasList()) {
                if (eventType == EventType.DELETE) { 
                	// 数据删除了,redis中也同步数据
                    printColumn(rowData.getBeforeColumnsList());
                    redisDelete(entry.getHeader().getTableName(), rowData.getBeforeColumnsList());
                } else if (eventType == EventType.INSERT) { 
                	// 新增了数据,redis中也同步数据
                    printColumn(rowData.getAfterColumnsList());
                    redisInsert(entry.getHeader().getTableName(), rowData.getAfterColumnsList());
                } else { 
                	// update修改数据等等...,redis中也同步数据
                    System.out.println("-------> before");
                    printColumn(rowData.getBeforeColumnsList());
                    System.out.println("-------> after");
                    printColumn(rowData.getAfterColumnsList());
                    redisUpdate(entry.getHeader().getTableName(), rowData.getAfterColumnsList());
                }
            }
        }
    }

    /** 打印日志 */
    private static void printColumn(List<Column> columns) {
        for (Column column : columns) {
            System.out.println(column.getName() + " : " + column.getValue() + "    update=" + column.getUpdated());
        }
    }
    
    /*
     * redis中新增k-v信息
     */
    private static void redisInsert(String tableName, List<Column> columns) throws JsonProcessingException {
        HashMap<String, Object> map = new HashMap<String, Object>();
        for (Column column : columns) {
            map.put(column.getName(), column.getValue());
        }
        ObjectMapper mapper = new ObjectMapper();
        // 类型根据配置而定
        String value = mapper.writeValueAsString(map);

        if (columns.size() > 0) {
            jedis.set(tableName + "::" + columns.get(0).getValue(), value);
        }
    }
    
    /*
     * redis中修改k-v信息
     */
    private static void redisUpdate(String tableName, List<Column> columns) throws JsonProcessingException {
        HashMap<String, Object> map = new HashMap<String, Object>();
        for (Column column : columns) {
            map.put(column.getName(), column.getValue());
        }
        ObjectMapper mapper = new ObjectMapper();
        String value = mapper.writeValueAsString(map);

        if (columns.size() > 0) {
            jedis.set(tableName + "::" + columns.get(0).getValue(), value);
        }
    }
    
    /*
     * redis中删除k-v信息
     */
    private static void redisDelete(String tableName, List<Column> columns) throws JsonProcessingException {
        HashMap<String, Object> map = new HashMap<String, Object>();
        for (Column column : columns) {
            map.put(column.getName(), column.getValue());
        }
        /*
        ObjectMapper mapper = new ObjectMapper();
        String value = mapper.writeValueAsString(map);
        */
        if (columns.size() > 0) {
            jedis.del(tableName + "::" + columns.get(0).getValue());
        }
    }

}  
