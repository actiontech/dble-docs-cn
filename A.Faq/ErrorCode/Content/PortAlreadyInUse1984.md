# dble-Port already in use:1984

## Issue

- wrapper.log-Error1
```
STATUS | wrapper | 2019/07/23 16:37:06 | --> Wrapper Started as Daemon
STATUS | wrapper | 2019/07/23 16:37:06 | Launching a JVM...
INFO | jvm 1 | 2019/07/23 16:37:06 | OpenJDK 64-Bit Server VM warning: ignoring option MaxPermSize=64M; support was removed in 8.0
INFO | jvm 1 | 2019/07/23 16:37:07 | 错误: 代理抛出异常错误: java.rmi.server.ExportException: Port already in use: 1984; nested exception is:
INFO | jvm 1 | 2019/07/23 16:37:07 | java.net.BindException: Address already in use (Bind failed)
INFO | jvm 1 | 2019/07/23 16:37:07 | sun.management.AgentConfigurationError: java.rmi.server.ExportException: Port already in use: 1984;
```

- wrapper.log-Error2
```
STATUS | wrapper  | 2019/07/26 16:12:48 | --> Wrapper Started as Daemon
STATUS | wrapper  | 2019/07/26 16:12:49 | Launching a JVM...
INFO   | jvm 1    | 2019/07/26 16:12:49 | Wrapper (Version 3.2.3) http://wrapper.tanukisoftware.org
INFO   | jvm 1    | 2019/07/26 16:12:49 |   Copyright 1999-2006 Tanuki Software, Inc.  All Rights Reserved.
INFO   | jvm 1    | 2019/07/26 16:12:49 |
INFO   | jvm 1    | 2019/07/26 16:12:51 | java.net.BindException: Address already in use
INFO   | jvm 1    | 2019/07/26 16:12:51 |       at sun.nio.ch.Net.bind0(Native Method)
INFO   | jvm 1    | 2019/07/26 16:12:51 |       at sun.nio.ch.Net.bind(Net.java:433)
INFO   | jvm 1    | 2019/07/26 16:12:51 |       at sun.nio.ch.Net.bind(Net.java:425)
INFO   | jvm 1    | 2019/07/26 16:12:51 |       at sun.nio.ch.ServerSocketChannelImpl.bind(ServerSocketChannelImpl.java:223)
INFO   | jvm 1    | 2019/07/26 16:12:51 |       at com.actiontech.dble.net.NIOAcceptor.<init>(NIOAcceptor.java:46)
```

## Resolution

- 根据Error1：  
修改配置文件wrapper.conf：修改被占用端口1984  
-Dcom.sun.management.jmxremote.port=1984
- 根据Error2：  
netstat -nap 查看程序运行的pid（8066和9066依然存在）  
kill -9 pid 杀掉进程
- 启动dble成功

## Root Cause

- 已经启动过一个开启jmx服务的java程序后，再启动dble会报这个错；
- dble启动过程中会占用三个端口：业务端口，管理端口，jvm对外提供jmx服务端口；
- jmx可通过jconsole连接上jvm，观测jvm的运行状态。

## Relevant Content

1. JVM  
JVM是一种使用软件模拟出来的计算机，它用于执行Java程序，有一套非常严格的技术规范，是Java跨平台的依赖基础。  
Java虚拟机有自己想象中的硬件，如处理器，堆栈，寄存器等，还有相应的指令系统它允许Java程序就好像一台计算机允许c或c++程序一样。

2. JMX  
所谓JMX，是Java Management Extensions的缩写，是Java管理系统的一个标准、一个规范，也是一个接口，一个框架。  
它和JPA、JMS是一样的，就是通过将监控和管理涉及到的各个方面的问题和解决办法放到一起，统一设计，以便向外提供服务，以供使用者调用。

3. Jconsole  
Jconsole是JDK自带的监控工具，它用于连接正在运行的本地或者远程的JVM，对运行在java应用程序的资源消耗和性能进行监控，并画出大量的图表，提供强大的可视化界面。  
自身占用的服务器内存很小，甚至可以说几乎不消耗。
