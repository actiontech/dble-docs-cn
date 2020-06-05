# dble-Can't get variables from shardingNode

## Setting



- db.xml 片段

```
<dbGroup name="localhost1" rwSplitMode="0" delayThreshold="100">
<heartbeat>show slave status</heartbeat>
<dbInstance host="hostM1" url="localhost:3306" user="root"  password="nE7jA%5m" maxCon="1000" minCon="10" primary="true" >  </dbInstance>
</dbGroup>
<dbGroup name="localhost2" rwSplitMode="0" delayThreshold="100">
<heartbeat>show slave status</heartbeat>
<dbInstance host="hostM2" url="localhost:3306" user="root"  password="nE7jA%5m" maxCon="1000" minCon="10" primary="true">  </dbInstance>  </dbGroup>
```

## Issue

- 查看dble启动日志：

```
Running dble-server...
wrapper | --> Wrapper Started as Console
wrapper | Launching a JVM...
jvm 1   | Wrapper (Version 3.2.3)
http://wrapper.tanukisoftware.org
jvm 1   | Copyright 1999-2006 Tanuki Software, Inc. All Rights Reserved.
jvm 1   |
jvm 1   | java.io.IOException:Can't get variables from shardingNode  ...
wrapper | <-- Wrapper Stopped
```

## Resolution

1. 检查mysql版本及是否正常启动，不支持mysql5.1，请升级mysql, 如果启动正常见下一步；
2. db.xml中的root用户能否通过配置文件的信息连接mysql，连接成功见下一步；
3. 检查root用户权限；
4. 连接mysql，并执行show variables命令，未执行成功见下一步；
5. 修改配置文件中dbGroup指定的后端数据库密码，更新配置文件，dble正常启动

## Root Cause

- 通过db.xml 配置信息成功连接mysql后，并不能执行show variables
- 由于mysql 5.7 初始化之后，首次使用随机密码登陆，没有修改密码，无法对数据库进行操作

## Relevant Content

1. 安装好mysql5.7后，第一次初始化数据库
2. 随机密码登录mysql，首次登录后，mysql要求必须修改默认密码，否则不能执行任何其他数据库操作，这样体现了不断增强的Mysql安全性。
3. 第一次登陆后必须更改密码：
	- mysql> show databases;
	- ERROR 1820 (HY000): You must reset your password using ALTER USER statement before executing this statement.
	- mysql > set password = password('xxxxxx');
