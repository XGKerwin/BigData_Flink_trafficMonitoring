package com.msb.cityTraffic.utils

import java.util
import org.apache.flink.configuration
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.{HConnection, HConnectionManager, HTableInterface, Put}

/**
 * @author: AIY
 * @email: aiykerwin@sina.com
 * @Date: 2022/7/23 10:46
 */
class HbaseWriterDataSink extends RichSinkFunction[java.util.List[Put]] {

  // 创建连接
  var conn: HConnection = _
  // 配置信息
  var conf: Configuration = _

  //初始化Hbase的链接
  override def open(parameters: configuration.Configuration): Unit = {
    conf = HBaseConfiguration.create()
    conf.set("hbase.zookeeper.quorum", "node01:2181")
    conn = HConnectionManager.createConnection(conf) //hbase数据库连接池
  }

  override def close(): Unit = {
    // 关掉连接
    conn.close()
  }

  // 往Hbase中写数据
  override def invoke(value: util.List[Put], context: SinkFunction.Context[_]): Unit = {
    val htable: HTableInterface = conn.getTable("t_track_info") // 从连接中拿到表对象
    htable.put(value) // 将数据写入
    htable.close() //关闭连接
  }
}
