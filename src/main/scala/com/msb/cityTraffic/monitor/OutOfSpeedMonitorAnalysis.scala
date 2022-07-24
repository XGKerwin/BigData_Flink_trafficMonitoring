package com.msb.cityTraffic.monitor

import com.msb.cityTraffic.utils.{GlobalConstants, JdbcReadDataSource, MonitorInfo, OutOfLimitSpeedInfo, TrafficInfo, WriteDataSink}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment

import java.util.Properties
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.datastream.BroadcastStream
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector

/**
 * @author: AIY
 * @email: aiykerwin@sina.com
 * @Date: 2022/7/22 9:57
 */

/**
 * 实时车辆超速监控功能   从kafka中获取数据 然后读取mysql的限制信息 将超速的车辆信息 添加到mysql数据库中
 * 数据库  t_monitor_info  t_speeding_info
 */
object OutOfSpeedMonitorAnalysis {

  def main(args: Array[String]): Unit = {
    val streamEnv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    import org.apache.flink.streaming.api.scala._

    // stream1  海量的数据流，不可以存入广播状态流中
    // stream2  从Mysql数据库中读取的卡口限速信息    数据量少，更新不频繁
    // 先创建第二个流 只有第二个流有数据了才能判断是否超速
    val stream2: BroadcastStream[MonitorInfo] = streamEnv.addSource(new JdbcReadDataSource[MonitorInfo](classOf[MonitorInfo]))
      .broadcast(GlobalConstants.MONITOR_STATE_DESCRIPTOR)

    //创建kafka连接属性
    val props = new Properties()
    props.setProperty("bootstrap.servers", "node01:9092,node02:9092,node03:9092")
    props.setProperty("group.id", "msb_001") // 定义一个消费组

    // 创建一个Kafka的Source   stream1的数据流很大不能存入广播状态流中
    val stream1: DataStream[TrafficInfo] = streamEnv.addSource(
      new FlinkKafkaConsumer[String]("t_traffic_msb", new SimpleStringSchema(), props).setStartFromEarliest() //从第一行开始读取数据
    )
      //    val stream1: DataStream[TrafficInfo] = streamEnv.socketTextStream("node01", 9999)
      .map(line => {
        var arr = line.split(",")
        // 车辆经过卡口信息     时间戳 卡口Id 摄像头Id 车牌 车速 道路Id 区域id
        new TrafficInfo(arr(0).toLong, arr(1), arr(2), arr(3), arr(4).toDouble, arr(5), arr(6))
      })



    // Flink中有 connect 和 join 两种连接方式   join一般做有条件的连接（内连接）   connect内外连接都可以
    stream1.connect(stream2)
      // 使用process函数 定义  三个类型  车辆经过卡口的信息  卡口信息的样例类   车辆超速的信息
      .process(new BroadcastProcessFunction[TrafficInfo, MonitorInfo, OutOfLimitSpeedInfo] {
        override def processElement(value: TrafficInfo, ctx: BroadcastProcessFunction[TrafficInfo, MonitorInfo, OutOfLimitSpeedInfo]#ReadOnlyContext, out: Collector[OutOfLimitSpeedInfo]) = {
          // 先从状态中得到当前卡口的限速信息       根据卡口id拿到限速信息
          val info: MonitorInfo = ctx.getBroadcastState(GlobalConstants.MONITOR_STATE_DESCRIPTOR).get(value.monitorId)
          if (info != null) { // 只要不为空就代表经过的卡口是有限速的
            var limitSpeed = info.limitSpeed // 限速信息
            var realSpeed = value.speed // 当前车速
            if (limitSpeed * 1.1 < realSpeed) { // 超过10%被判定为超速
              out.collect(new OutOfLimitSpeedInfo(value.car, value.monitorId, value.roadId, realSpeed, limitSpeed, value.actionTime))
            }
          }
        }

        // 广播流
        override def processBroadcastElement(value: MonitorInfo, ctx: BroadcastProcessFunction[TrafficInfo, MonitorInfo, OutOfLimitSpeedInfo]#Context, out: Collector[OutOfLimitSpeedInfo]) = {
          // 把广播流中的数据保存到状态中   拿到流的对象
          ctx.getBroadcastState(GlobalConstants.MONITOR_STATE_DESCRIPTOR).put(value.monitorId, value)
        }
      }).print()
//      .addSink(new WriteDataSink[OutOfLimitSpeedInfo](classOf[OutOfLimitSpeedInfo])) // 写入mysql数据库

    streamEnv.execute()


  }


}
