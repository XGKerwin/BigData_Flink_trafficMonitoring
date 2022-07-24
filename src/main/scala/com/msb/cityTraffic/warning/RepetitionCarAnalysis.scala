package com.msb.cityTraffic.warning

import java.util.Properties
import com.msb.cityTraffic.utils.{RepetitionCarWarning, TrafficInfo, WriteDataSink}
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.util.Collector

/**
 * @author: AIY
 * @email: aiykerwin@sina.com
 * @Date: 2022/7/22 19:53
 */

/**
 * 套牌车分析
 * 按照10秒内同一个车牌出现在监控下进行判断是否涉嫌套牌
 * 使用状态编程
 *
 * 1592722030000,0002,16278,皖M16450,116.3,30,83
 * 1592722040000,0003,16278,皖M16450,116.3,30,83
 * 时间相隔太短
 */
object RepetitionCarAnalysis {

  def main(args: Array[String]): Unit = {
    val streamEnv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    import org.apache.flink.streaming.api.scala._

    streamEnv.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    streamEnv.setParallelism(1)

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
      }) // 引入时间
      .assignAscendingTimestamps(_.actionTime)

    stream.keyBy(_.car) // 根据车牌号分
      .process(new KeyedProcessFunction[String, TrafficInfo, RepetitionCarWarning] {
        // 定义一个状态   在10秒内同时出现了   保存第一辆车出现的信息
        lazy val firstState = getRuntimeContext.getState(new ValueStateDescriptor[TrafficInfo]("first", classOf[TrafficInfo]))

        override def processElement(i: TrafficInfo, context: KeyedProcessFunction[String, TrafficInfo, RepetitionCarWarning]#Context, collector: Collector[RepetitionCarWarning]) = {
          val first: TrafficInfo = firstState.value() // 拿到第一辆车的信息
          if (first == null) { // 当前这量车就是第一辆车  代表还没有出现过这辆车
            firstState.update(i) // 将第一辆车放到状态中
          } else { // 有两辆车出现了    第一种情况已经超过了10秒钟    如果出现第二种情况没有超过10秒钟可能就涉嫌套牌了
            val nowTime = i.actionTime // 拿到当前时间
            val firstTime = first.actionTime // 拿到第一辆车的时间
            var less: Long = (nowTime - firstTime).abs / 1000 // 计算出时间差
            if (less <= 10) { // 如果小于等于10秒就涉嫌套牌了
              // 拿到警告信息
              var warn = new RepetitionCarWarning(i.car, if (nowTime > firstTime) first.monitorId else i.monitorId,
                if (nowTime < firstTime) first.monitorId else i.monitorId, "涉嫌套牌车", context.timerService().currentProcessingTime()
              )
              collector.collect(warn) // 收集数据
              firstState.clear() // 清空状态
            } else { //不是套牌车，把第二次经过卡口的数据保存到状态中，以便下次判断
              if (nowTime > firstTime) firstState.update(i)
            }
          }
        }
      })
      .addSink(new WriteDataSink[RepetitionCarWarning](classOf[RepetitionCarWarning]))

    streamEnv.execute()
  }


}
