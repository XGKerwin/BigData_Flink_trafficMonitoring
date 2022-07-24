package com.msb.cityTraffic.warning

import java.sql.DriverManager
import java.util
import java.util.Properties

import com.msb.cityTraffic.utils._
import org.apache.flink.api.common.functions.{RichFilterFunction, RichMapFunction}
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.BroadcastStream
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.util.Bytes


/**
 * @author: AIY
 * @email: aiykerwin@sina.com
 * @Date: 2022/7/23 10:25
 */
object CarTrackInfoAnalysis {

  def main(args: Array[String]): Unit = {
    val streamEnv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    import org.apache.flink.streaming.api.scala._

    val stream2 = streamEnv.addSource(new JdbcReadDataSource[ViolationInfo](classOf[ViolationInfo]))
      .broadcast(GlobalConstants.VIOLATION_STATE_DESCRIPTOR)

    val props = new Properties()
    props.setProperty("bootstrap.servers", "node01:9092,node02:9092,node03:9092")
    props.setProperty("group.id", "msb_001")

    val stream1: DataStream[TrafficInfo] = streamEnv.addSource(new FlinkKafkaConsumer[String]("t_traffic_msb", new SimpleStringSchema(), props).setStartFromEarliest())
      //    val stream1: DataStream[TrafficInfo] = streamEnv.readTextFile("C:\\Users\\AIY\\IdeaProjects\\BigData_Flink_trafficMonitoring\\src\\main\\resources\\log_2020-06-21_0.log") // 读取文件中的数据
      .map(line => {
        val arr = line.split(",")
        new TrafficInfo(arr(0).toLong, arr(1), arr(2), arr(3), arr(4).toDouble, arr(5), arr(6))
      })

    //批量写入数据到Hbase表中，启动CountWindow 来完成批量插入，批量的条数由窗口大小决定
    //开窗
    stream1.connect(stream2) //全局的count窗口
      .process(new BroadcastProcessFunction[TrafficInfo, ViolationInfo, TrackInfo] {
        //

        override def processElement(info: TrafficInfo, readOnlyContest: BroadcastProcessFunction[TrafficInfo, ViolationInfo, TrackInfo]#ReadOnlyContext, collector: Collector[TrackInfo]): Unit = {
          val violationInfo: ViolationInfo = readOnlyContest.getBroadcastState(GlobalConstants.VIOLATION_STATE_DESCRIPTOR).get(info.car)
          if (violationInfo != null) { // 当前车辆就是违法车辆
            // 将违法车辆收集
            collector.collect(new TrackInfo(info.car, info.actionTime, info.monitorId, info.roadId, info.areaId, info.speed))
          }
        }

        override def processBroadcastElement(violationInfo: ViolationInfo, context: BroadcastProcessFunction[TrafficInfo, ViolationInfo, TrackInfo]#Context, out: Collector[TrackInfo]): Unit = {
          context.getBroadcastState(GlobalConstants.VIOLATION_STATE_DESCRIPTOR).put(violationInfo.car, violationInfo)
        }
      }) // 把数据写入hbase中 批量写入hbase  每次写入10条数据   Flink使用countwindow性能高一点
      .countWindowAll(10)
      .apply((win: GlobalWindow, input: Iterable[TrackInfo], out: Collector[java.util.List[Put]]) => {
        val list = new util.ArrayList[Put]()
        for (info <- input) {
          // 再hbase表中为了方便查询每辆车最近的车辆轨迹 根据车辆通行的时间降序排序
          var put = new Put(Bytes.toBytes(info.car + "_" + (Long.MaxValue - info.actionTime)))
          put.add("cf1".getBytes(), "car".getBytes(), Bytes.toBytes(info.car))
          put.add("cf1".getBytes(), "actionTime".getBytes(), Bytes.toBytes(info.actionTime))
          put.add("cf1".getBytes(), "monitorId".getBytes(), Bytes.toBytes(info.monitorId))
          put.add("cf1".getBytes(), "roadId".getBytes(), Bytes.toBytes(info.roadId))
          put.add("cf1".getBytes(), "areaId".getBytes(), Bytes.toBytes(info.areaId))
          put.add("cf1".getBytes(), "speed".getBytes(), Bytes.toBytes(info.speed))
          list.add(put)
        }
        out.collect(list)
      })
      .addSink(new HbaseWriterDataSink)

    streamEnv.execute()
  }

  //自定义的过滤函数类，把违法车辆信息留下，其他都去掉
  class MyViolationRichFilterFunction extends RichFilterFunction[TrafficInfo] {
    //map集合违法车辆信息
    var map = scala.collection.mutable.Map[String, ViolationInfo]()

    //一次性从数据库中读所有的违法车辆信息列表存放到Map集合中，open函数在计算程序初始化的时候调用的，
    override def open(parameters: Configuration): Unit = {
      var conn = DriverManager.getConnection("jdbc:mysql://localhost/traffic_monitor", "root", "1234")
      var pst = conn.prepareStatement("select car ,violation, create_time from t_violation_list")
      var set = pst.executeQuery()
      while (set.next()) {
        var info = new ViolationInfo(set.getString(1), set.getString(2), set.getLong(3))
        map.put(info.car, info)
      }
      set.close()
      pst.close()
      conn.close()
    }

    //过滤
    override def filter(t: TrafficInfo): Boolean = {
      val o: Option[ViolationInfo] = map.get(t.car)
      if (o.isEmpty) {
        false
      } else {
        true
      }
    }
  }

}
