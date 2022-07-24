package com.msb.cityTraffic.warning

import java.sql.DriverManager
import java.util.Properties

import com.msb.cityTraffic.utils.{DangerousDrivingWarning, MonitorInfo, OutOfLimitSpeedInfo, TrafficInfo}
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.cep.scala.{CEP, PatternStream}
import org.apache.flink.cep.scala.pattern.Pattern
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.time.Time

import scala.collection.Map

/**
 * @author: AIY
 * @email: aiykerwin@sina.com
 * @Date: 2022/7/22 21:11
 */

/**
 * 实时危险驾驶分析
 * 同一辆车在2分钟内，超速通过卡口超过3次以上 并且每次超速超过规定的20%以上 这样的机动车涉嫌危险驾驶
 * CEP编程
 *
 * 测试数据
 * 1592722028000,0001,16278,皖M16450,116.3,30,83
 * 1592722031000,0001,16278,皖M16450,116.3,30,83
 * 1592722035000,0008,16278,皖M16450,116.3,30,83
 * 1592722140000,0009,16278,皖M16450,116.3,30,83
 *
 */
object DangerousDriverWarning {

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
    val stream: DataStream[OutOfLimitSpeedInfo] = streamEnv.socketTextStream("node01", 9999)
      .map(line => {
        var arr = line.split(",")
        new TrafficInfo(arr(0).toLong, arr(1), arr(2), arr(3), arr(4).toDouble, arr(5), arr(6))
      }) //引入事件时间,Watermark ,数据迟到5秒
      .map(new MyRichMapFunction(60)) // 给没有规定限速的设定一个默认的限速
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[OutOfLimitSpeedInfo](Time.seconds(5)) { // 数据迟到5秒
        override def extractTimestamp(t: OutOfLimitSpeedInfo) = t.actionTime
      })

    // 使用CEP编程
    var pattern = Pattern.begin[OutOfLimitSpeedInfo]("begin") // 定义一个cep编程模式  并定义一个名字
      .where(t => {
        t.limitSpeed * 1.2 < t.realSpeed // 超速20%
      }).timesOrMore(3) // 超速3次或者3次以上
      .greedy //贪婪模式尽可能多的匹配
      .within(Time.minutes(2)) //定义时间范围是2分钟内

    // 检测流   使用keyBy对每辆车进行分组
    val ps: PatternStream[OutOfLimitSpeedInfo] = CEP.pattern(stream.keyBy(_.car), pattern)
    ps.select(map => { // map只有一条
      // list集合中存有3条以上的车辆违章信息
      import scala.collection.JavaConverters._

      val list: List[OutOfLimitSpeedInfo] = map.get("begin").get.toList
      var sb = new StringBuilder()
      sb.append("该车辆涉嫌危险驾驶,")
      var i = 1
      for (info <- list) {
        sb.append(s"第${i}个经过卡口是:${info.monitorId} -->") //
        i += 1
      }
      // 车牌 警告信息 时间
      new DangerousDrivingWarning(list(0).car, sb.toString(), System.currentTimeMillis(), 0)
    }).print()

    streamEnv.execute()

  }

  class MyRichMapFunction(defaultLimit: Int) extends RichMapFunction[TrafficInfo, OutOfLimitSpeedInfo] {
    var map = scala.collection.mutable.Map[String, MonitorInfo]() // 使用map集合来接收卡口的信息

    // 一次性从数据库中读取所有卡口的限速  将限制信息全部放到map集合中
    override def open(parameters: Configuration): Unit = {
      var conn = DriverManager.getConnection("jdbc:mysql://localhost/traffic_monitor", "root", "1234")
      var pst = conn.prepareStatement("select monitor_id,road_id,speed_limit,area_id from t_monitor_info where speed_limit > 0")
      var set = pst.executeQuery()
      while (set.next()) {
        var info = new MonitorInfo(set.getString(1), set.getString(2), set.getInt(3), set.getString(4))
        map.put(info.monitorId, info)
      }
      // 关闭所有对象
      set.close()
      pst.close()
      conn.close()
    }

    /**
     * 执行map方法的时候map集合中就有所有的卡口信息了  然后进行超速判断
     *
     * @param in
     * @return
     */
    override def map(in: TrafficInfo): OutOfLimitSpeedInfo = {
      // 首先从Map集合中判断是否存在卡口的限速，如果不存在，默认限速为60
      val info = map.getOrElse(in.monitorId, new MonitorInfo(in.monitorId, in.roadId, defaultLimit, in.areaId)) // 根据卡口编号  如果没有卡口信息的话就返回一个默认的卡口信息
      new OutOfLimitSpeedInfo(in.car, in.monitorId, in.roadId, in.speed, info.limitSpeed, in.actionTime) //
    }
  }
}
