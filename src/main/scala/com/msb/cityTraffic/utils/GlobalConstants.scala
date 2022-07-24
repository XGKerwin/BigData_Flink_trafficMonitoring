package com.msb.cityTraffic.utils

import org.apache.flink.api.common.state.MapStateDescriptor

/**
 * @author: AIY
 * @email: aiykerwin@sina.com
 * @Date: 2022/7/22 9:44
 */


/**
 * 从Kafka中读取的数据类型，车辆经过卡口的信息
 *
 * @param actionTime 时间戳
 * @param monitorId  卡口Id
 * @param cameraId   摄像头Id
 * @param car        车牌
 * @param speed      车速
 * @param roadId     道路Id
 * @param areaId     区域id
 */
case class TrafficInfo(actionTime: Long, monitorId: String, cameraId: String, car: String, speed: Double, roadId: String, areaId: String)

/**
 * 卡口信息的样例类
 *
 * @param monitorId  卡口Id
 * @param roadId     道路Id
 * @param limitSpeed 限速
 * @param areaId     区域Id
 */
case class MonitorInfo(monitorId: String, roadId: String, limitSpeed: Int, areaId: String)

/**
 * 车辆超速的信息
 *
 * @param car        车牌
 * @param monitorId  卡口Id
 * @param roadId     道路Id
 * @param realSpeed  速度
 * @param limitSpeed 限速
 * @param actionTime 时间戳
 */
case class OutOfLimitSpeedInfo(car: String, monitorId: String, roadId: String, realSpeed: Double, limitSpeed: Int, actionTime: Long)

/**
 * 某个时间范围内卡口的平均车速和通过的车辆数量
 *
 * @param start     开始时间
 * @param end       结束时间
 * @param monitorId 卡口id
 * @param avgSpeed  平均车速
 * @param carCount  车辆数量
 */
case class AvgSpeedInfo(start: Long, end: Long, monitorId: String, avgSpeed: Double, carCount: Int)

/**
 * 套牌车辆告警信息对象
 *
 * @param car           车牌
 * @param firstMonitor  第一个监视器
 * @param secondMonitor 第二个监视器
 * @param msg           警告信息
 * @param action_time   时间
 */
case class RepetitionCarWarning(car: String, firstMonitor: String, secondMonitor: String, msg: String, action_time: Long)


/**
 * 危险驾驶的信息
 *
 * @param car         车牌
 * @param msg
 * @param create_time 创建时间
 * @param avgSpeed    平均车速
 */
case class DangerousDrivingWarning(car: String, msg: String, create_time: Long, avgSpeed: Double)

/**
 * 违法车辆信息对象
 *
 * @param car 车牌
 * @param msg
 * @param createTime
 */
case class ViolationInfo(car: String, msg: String, createTime: Long)

/**
 * 车辆轨迹数据样例类
 *
 * @param car        车牌
 * @param actionTime 时间戳
 * @param monitorId  监视器
 * @param roadId     道路Id
 * @param areaId     区域Id
 * @param speed      车速
 */
case class TrackInfo(car: String, actionTime: Long, monitorId: String, roadId: String, areaId: String, speed: Double)

object GlobalConstants {

  lazy val MONITOR_STATE_DESCRIPTOR = new MapStateDescriptor[String, MonitorInfo]("monitor_info", classOf[String], classOf[MonitorInfo])
  lazy val VIOLATION_STATE_DESCRIPTOR = new MapStateDescriptor[String, ViolationInfo]("violation_info", classOf[String], classOf[ViolationInfo])

}
