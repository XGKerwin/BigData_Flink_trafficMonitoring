package com.msb.cityTraffic.monitor

import java.util.Properties
import com.msb.cityTraffic.utils.{AvgSpeedInfo, GlobalConstants, JdbcReadDataSource, MonitorInfo, TrafficInfo, WriteDataSink}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.datastream.BroadcastStream
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector

/**
 * @author: AIY
 * @email: aiykerwin@sina.com
 * @Date: 2022/7/22 15:27
 */
object AvgSpeedByMonitorAnalysis {

  def main(args: Array[String]): Unit = {
    val streamEnv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    import org.apache.flink.streaming.api.scala._

    streamEnv.setStreamTimeCharacteristic(TimeCharacteristic.EventTime) // 设置一个时间语义 采用事件事件
    streamEnv.setParallelism(1) // 默认并行度为1

    val props = new Properties()
    props.setProperty("bootstrap.servers", "node01:9092,node02:9092,node03:9092")
    props.setProperty("group.id", "msb_001")

    //创建一个Kafka的Source
    //    val stream: DataStream[TrafficInfo] = streamEnv.addSource(
    //      new FlinkKafkaConsumer[String]("t_traffic_msb", new SimpleStringSchema(), props).setStartFromEarliest() //从第一行开始读取数据
    //    )
    val stream: DataStream[TrafficInfo] = streamEnv.socketTextStream("node01", 9999)
      .map(line => {
        var arr = line.split(",")
        new TrafficInfo(arr(0).toLong, arr(1), arr(2), arr(3), arr(4).toDouble, arr(5), arr(6))
      }) //引入Watermark，并且延迟时间为5秒
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[TrafficInfo](Time.seconds(5)) {
        override def extractTimestamp(element: TrafficInfo) = element.actionTime
      })

    stream.keyBy(_.monitorId) // 按照每个卡口编号分组
      .timeWindow(Time.minutes(5), Time.minutes(1)) //窗口长度5分钟 滑动步长1分钟
      .aggregate( //设计一个累加器：二元组(车速之后，车辆的数量)
        new AggregateFunction[TrafficInfo, (Double, Long), (Double, Long)] {
          // 初始化
          override def createAccumulator() = (0, 0)

          // 进行累加
          override def add(value: TrafficInfo, acc: (Double, Long)) = {
            (acc._1 + value.speed, acc._2 + 1) // 累加器的值相加    acc第一个元素加当前车的车速      acc第二个元素加 1  代表来一辆车+1
          }

          // 返回
          override def getResult(acc: (Double, Long)) = acc //将acc返回

          //合并
          override def merge(a: (Double, Long), b: (Double, Long)) = {
            (a._1 + b._1, a._2 + b._2) // 将两个累加器 相累加
          }
        },
        (k: String, w: TimeWindow, input: Iterable[(Double, Long)], out: Collector[AvgSpeedInfo]) => {
          val acc: (Double, Long) = input.last // 累加器的值
          var avg: Double = (acc._1 / acc._2).formatted("%.2f").toDouble // 平均车速 = acc1车速之和 acc2总数  精确到小数点后两位
          out.collect(new AvgSpeedInfo(w.getStart, w.getEnd, k, avg, acc._2.toInt)) //返回消息
        }
      )
      .addSink(new WriteDataSink[AvgSpeedInfo](classOf[AvgSpeedInfo]))

    streamEnv.execute()

  }

}
