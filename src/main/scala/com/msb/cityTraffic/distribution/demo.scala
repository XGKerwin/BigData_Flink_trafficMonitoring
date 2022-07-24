package com.msb.cityTraffic.distribution

import com.msb.cityTraffic.utils.TrafficInfo
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment

/**
 * @author: AIY
 * @email: aiykerwin@sina.com
 * @Date: 2022/7/23 17:56
 */
object demo {
  def main(args: Array[String]): Unit = {
    val streamEnv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    import org.apache.flink.streaming.api.scala._
    streamEnv.setParallelism(1)

    var str="C:\\Users\\AIY\\IdeaProjects\\BigData_Flink_trafficMonitoring\\src\\main\\resources\\log_2022-07-21_0.log"
    val stream= streamEnv.readTextFile(str)
    stream.print();

    streamEnv.execute();
  }

}
