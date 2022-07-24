# 项目

## Linux配置

修改环境变量

> [root@node01 flume-1.9.0]# vi ~/.bash_profile

添加

```properties
export FLUME_HOME=/opt/bdp/flume-1.9.0
export PATH=$PATH:$FLUME_HOME/bin
```

> [root@node01 flume-1.9.0]# source ~/.bash_profile

打印环境变量

> [root@node01 flume-1.9.0]# printenv

配置kafka环境

> [root@node01 opt]#  vim to_kafka.conf

```shell
# 设置source名称
a.sources = r1
# channel名称
a.channels = c1
# sink的名称
a.sinks = k1
# 设置source类型为TAILDIR 监控目录下的文件
a.sources.r1.type=TAILDIR
# 文件的组可以定义多种
a.sources.r1.filegroups = f1
# 第一组监控的是test1的文件夹中的什么文件 ./log文件
a.sources.r1.filegroups.f1 = /opt/logs/common/.*log

# 设置source的channel名称
a.sources.r1.channels = c1
a.sources.r1.max-line-length = 1000000
# 设置channel的类型
a.channels.c1.type = memory
# 设置channel管道中最大可以存储的event数量
a.channels.c1.capacity = 1000
# 每次最大从source获取或者发送到sink中的数据量
a.channels.c1.transcationCapacity=100

# 设置kafka接收器
a.sinks.k1.type = org.apache.flume.sink.kafka.KafkaSink
# 设置kafka的broker地址和端口号
a.sinks.k1.kafka.bootstrap.servers=node01:9092,node02:9092,node03:9092
# 设置kafka的topic
a.sinks.k1.kafka.topic=t_traffic_msb
# 设置序列化方式
a.sinks.k1.serializer.class=kafka.serializer.StringEncoder
# 设置sink的channel名称
a.sinks.k1.channel = c1
```

将项目打成jar包上传到linux下

> [root@node01 opt]# java -jar interface_server-0.0.1-SNAPSHOT.jar 

运行jar包

**创建kafka topic**

启动kafka

cd /opt/bdp/kafka_2.13-3.0.1

> nohup bin/kafka-server-start.sh config/server.properties 1>/dev/null 2>&1 &

**json数据导入**

> cat data.json | kafka-console-producer.sh --broker-list node01:9092,node02:9092,node03:9092 --topic accessyjx

**启动生产者**

> [root@node01 kafka_2.13-3.0.1]# bin/kafka-console-producer.sh --broker-list node01:9092,node02:9092,node03:9092 --topic accessyjx

粘贴数据

```json
{"carrier":"中国联通","deviceId":"3b69ad66-1777-4649-ad26-ea74290959ad","deviceType":"MEIZU-ML5","eventId":"appLaunch","id":1821,"isNew":0,"lastUpdate":2021,"latitude":22.332239501356884,"longitude":99.583622711817,"netType":"3G","osName":"android","osVersion":"6.5","releaseChannel":"华为应用市场","resolution":"1366*768","sessionId":"IsGbo5Eu9EmT","timestamp":1618020107989}
```

**查看kafka数据**

> kafka-console-consumer.sh --bootstrap-server node01:9092,node02:9092,node03:9092 --topic t_traffic_msb --from-beginning

**创建一个Topic**

> 2.0 版本
>
> kafka-topics.sh  --create --zookeeper node01:2181 --partitions 3 --replication-factor 2 --topic t_traffic_msb
>
> 3.0
>
> kafka-topics.sh  --bootstrap-server node01:9092 --topic t_traffic_msb --replication-factor 3 --partitions 3 --create

查看topic中的数据

> [root@node01 ~]# kafka-console-consumer.sh --bootstrap-server node01:9092,node02:9092,node03:9092 --topic t_traffic_msb --from-beginning

**启动flume**

> [root@node01 opt]# flume-ng agent -n a -f ./to_kafka.conf -Dflume.root.logger=INFO,console

idea发送请求

**查看日志文件**

> tail -f xxxx

## SQL

```sql
/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 50709
 Source Host           : localhost:3306
 Source Schema         : traffic_monitor

 Target Server Type    : MySQL
 Target Server Version : 50709
 File Encoding         : 65001

 Date: 22/07/2022 15:01:56
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_area_info
-- ----------------------------
DROP TABLE IF EXISTS `t_area_info`;
CREATE TABLE `t_area_info`  (
  `area_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `area_name` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of t_area_info
-- ----------------------------
INSERT INTO `t_area_info` VALUES ('01', '海淀区');
INSERT INTO `t_area_info` VALUES ('02', '昌平区');
INSERT INTO `t_area_info` VALUES ('03', '朝阳区');
INSERT INTO `t_area_info` VALUES ('04', '顺义区');
INSERT INTO `t_area_info` VALUES ('05', '西城区');
INSERT INTO `t_area_info` VALUES ('06', '东城区');
INSERT INTO `t_area_info` VALUES ('07', '大兴区');
INSERT INTO `t_area_info` VALUES ('08', '石景山');

-- ----------------------------
-- Table structure for t_average_speed
-- ----------------------------
DROP TABLE IF EXISTS `t_average_speed`;
CREATE TABLE `t_average_speed`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `start_time` bigint(20) NULL DEFAULT NULL,
  `end_time` bigint(20) NULL DEFAULT NULL,
  `monitor_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `avg_speed` double NULL DEFAULT NULL,
  `car_count` int(11) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of t_average_speed
-- ----------------------------

-- ----------------------------
-- Table structure for t_monitor_info
-- ----------------------------
DROP TABLE IF EXISTS `t_monitor_info`;
CREATE TABLE `t_monitor_info`  (
  `monitor_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `road_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `speed_limit` int(11) NULL DEFAULT NULL,
  `area_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`monitor_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of t_monitor_info
-- ----------------------------
INSERT INTO `t_monitor_info` VALUES ('0000', '02', 60, '01');
INSERT INTO `t_monitor_info` VALUES ('0001', '02', 60, '02');
INSERT INTO `t_monitor_info` VALUES ('0002', '03', 80, '01');
INSERT INTO `t_monitor_info` VALUES ('0004', '05', 100, '03');
INSERT INTO `t_monitor_info` VALUES ('0005', '04', 0, NULL);
INSERT INTO `t_monitor_info` VALUES ('0021', '04', 0, NULL);
INSERT INTO `t_monitor_info` VALUES ('0023', '05', 0, NULL);
INSERT INTO `t_monitor_info` VALUES ('1010', '04', 90, NULL);
INSERT INTO `t_monitor_info` VALUES ('2888', '03', 100, NULL);
INSERT INTO `t_monitor_info` VALUES ('6666', '05', 80, NULL);
INSERT INTO `t_monitor_info` VALUES ('8506', '08', 80, NULL);

-- ----------------------------
-- Table structure for t_speeding_info
-- ----------------------------
DROP TABLE IF EXISTS `t_speeding_info`;
CREATE TABLE `t_speeding_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `car` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `monitor_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `road_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `real_speed` double NULL DEFAULT NULL,
  `limit_speed` int(11) NULL DEFAULT NULL,
  `action_time` bigint(20) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of t_speeding_info
-- ----------------------------

-- ----------------------------
-- Table structure for t_track_info
-- ----------------------------
DROP TABLE IF EXISTS `t_track_info`;
CREATE TABLE `t_track_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `car` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `action_time` bigint(20) NULL DEFAULT NULL,
  `monitor_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `road_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `area_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `speed` double NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of t_track_info
-- ----------------------------

-- ----------------------------
-- Table structure for t_violation_list
-- ----------------------------
DROP TABLE IF EXISTS `t_violation_list`;
CREATE TABLE `t_violation_list`  (
  `car` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `violation` varchar(1000) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `create_time` bigint(20) NULL DEFAULT NULL,
  PRIMARY KEY (`car`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of t_violation_list
-- ----------------------------
INSERT INTO `t_violation_list` VALUES ('京P22222', '违章未处理超过432次', NULL);
INSERT INTO `t_violation_list` VALUES ('京P33333', '违章未处理超过123次', NULL);
INSERT INTO `t_violation_list` VALUES ('京P44444', '嫌疑套牌车', NULL);
INSERT INTO `t_violation_list` VALUES ('京P55555', '嫌疑套牌车', NULL);
INSERT INTO `t_violation_list` VALUES ('京P66666', '嫌疑套牌车', NULL);
INSERT INTO `t_violation_list` VALUES ('京P77777', '违章未处理超过567次', NULL);
INSERT INTO `t_violation_list` VALUES ('京P88888', '违章未处理超过89次', NULL);
INSERT INTO `t_violation_list` VALUES ('京P99999', '违章未处理超过239次', NULL);

SET FOREIGN_KEY_CHECKS = 1;

```



## 测试数据

### OutOfSpeedMonitorAnalysis测试

> [root@node01 ~]# nc -lk 9999

```scala
    val stream1: DataStream[TrafficInfo] = streamEnv.socketTextStream("node01", 9999)
      .map(line => {
        var arr = line.split(",")
        // 车辆经过卡口信息     时间戳 卡口Id 摄像头Id 车牌 车速 道路Id 区域id
        new TrafficInfo(arr(0).toLong, arr(1), arr(2), arr(3), arr(4).toDouble, arr(5), arr(6))
      })
```

```bash
1592674820000,0000,76022,京J96816,54.0,86,06
1592674833000,0005,76022,京J96816,54.0,86,06
1592722028000,0001,16278,皖M16450,116.3,30,83
1592722030000,0002,16278,皖M16450,116.3,30,83
1592722040000,0003,16278,皖M16450,116.3,30,83
1592722035000,0004,16278,皖M16450,116.3,30,83
1592722030000,0002,16278,皖M16450,116.3,30,83
1592722055000,0003,16278,皖M16450,116.3,30,83
```

**真实测试**

运行OutOfSpeedMonitorAnalysis项目

打开创造数据的给kafka发送数据

如果数据量太少可以在t_speeding_info表中添加关口

### AvgSpeedByMonitorAnalysis测试

> [root@node01 ~]# nc -lk 9999

```shell
1592722028000,0001,16278,皖M16450,116.3,30,83
1592722031000,0001,16278,皖M16450,116.3,30,83
1592722035000,0008,16278,皖M16450,116.3,30,83
1592722140000,0009,16278,皖M16450,116.3,30,83
1592722148000,0001,16278,皖M16450,116.3,30,83
1592722095000,0001,16278,皖M16450,50,30,83
1592722145000,0001,16278,皖M16450,116.3,30,83
1592722205000,0001,16278,皖M16450,116.3,30,83


=
AvgSpeedInfo(1592721780000,1592722080000,0001,116.3,2)
AvgSpeedInfo(1592721780000,1592722080000,0008,116.3,1)
```

## HBase

```sql
hbase(main):001:0> create 't_track_info','cf1'

hbase(main):002:0> scan 't_track_info'
```

























