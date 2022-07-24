package com.msb.cityTraffic.distribution

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import java.util.Properties

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

/**
 * @author: AIY
 * @email: aiykerwin@sina.com
 * @Date: 2022/7/23 17:15
 */
object WriterDataToKafka {

  def main(args: Array[String]): Unit = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", "node01:9092,node02:9092,node03:9092")
    props.setProperty("key.serializer", classOf[StringSerializer].getName)
    props.setProperty("value.serializer", classOf[StringSerializer].getName)

    val producer = new KafkaProducer[String, String](props)
    var in = new BufferedReader(new InputStreamReader(new FileInputStream("C:\\Users\\AIY\\IdeaProjects\\BigData_Flink_trafficMonitoring\\src\\main\\resources\\log_2022-07-21_0.log")))

    var line = in.readLine()
    while (line != null) {
      val record: ProducerRecord[String, String] = new ProducerRecord[String, String]("t_traffic_msb", null, line)
      producer.send(record)
      line = in.readLine()
    }
    in.close()
    producer.close()
  }

}
