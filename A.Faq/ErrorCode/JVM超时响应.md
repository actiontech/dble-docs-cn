# dble-JVM超时响应   

## Setting  

1. 一个大查询，有排序 ,数据量比较大，1千万左右
2. wrapper是默认配置

## Issue  

- ERROR | wrapper | 2019/07/02 16:32:54 | JVM appears hung: Timed out waiting for signal from JVM. ERROR | wrapper | 2019/07/02 16:32:54 | JVM did not exit on request, terminated  INFO | wrapper | 2019/07/02 16:32:54 | JVM exited on its own while waiting to kill the application.  STATUS | wrapper | 2019/07/02 16:32:54 | JVM exited in response to signal SIGKILL (9).  STATUS | wrapper | 2019/07/02 16:32:59 | Launching a JVM...  INFO | jvm 3 | 2019/07/02 16:32:59 | OpenJDK 64-Bit Server VM warning: ignoring option MaxPermSize=64M; support was removed in 8.0 

## Resolution  

1. 根据内存情况调大wrapper配置中的MaxMetaspaceSize； 
> **注意**：默认基本是无穷大，建议设置这个参数，因为很可能会因为没有限制而导致metaspace被无止境使用(一般是内存泄漏)而被OS Kill。  

2. 根据内存情况调大wrapper配置中的MetaspaceSize 
> **注意**：默认20.8M左右，主要是控制metaspaceGC发生的初始阈值，也是最小阈值 

## Root Cause  

1. metaspace 空间达到阈值，导致fullgc，gc之后这部分空间没有被回收，不断触发fullgc，导致cpu增高，jvm无响应 
2. gc日志部分如下： 
> 2019-07-02T16:33:10.180+0800: 11.131: [GC (Metadata GC Threshold) 131272K->56716K(1267200K), 0.0655210 secs]  Heap after GC invocations=3 (full 0):  PSYoungGen total 567808K, used 43497K [0x000000076ab00000, 0x00000007b0000000, 0x00000007c0000000)  eden space 524288K, 0% used [0x000000076ab00000,0x000000076ab00000,0x000000078ab00000)  from space 43520K, 99% used [0x000000078ab00000,0x000000078d57a6a0,0x000000078d580000)  to space 43520K, 0% used [0x00000007ad580000,0x00000007ad580000,0x00000007b0000000)  ParOldGen total 699392K, used 13218K [0x00000006c0000000, 0x00000006eab00000, 0x000000076ab00000)  object space 699392K, 1% used [0x00000006c0000000,0x00000006c0ce89c8,0x00000006eab00000)  Metaspace used 20876K, capacity 21026K, committed 21296K, reserved 1069056K  class space used 2259K, capacity 2324K, committed 2432K, reserved 1048576K  }  {Heap before GC invocations=4 (full 1):  PSYoungGen total 567808K, used 43497K [0x000000076ab00000, 0x00000007b0000000, 0x00000007c0000000)  eden space 524288K, 0% used [0x000000076ab00000,0x000000076ab00000,0x000000078ab00000)  from space 43520K, 99% used [0x000000078ab00000,0x000000078d57a6a0,0x000000078d580000)  to space 43520K, 0% used [0x00000007ad580000,0x00000007ad580000,0x00000007b0000000)  ParOldGen total 699392K, used 13218K [0x00000006c0000000, 0x00000006eab00000, 0x000000076ab00000)  object space 699392K, 1% used [0x00000006c0000000,0x00000006c0ce89c8,0x00000006eab00000)  `Metaspace used 20876K, capacity 21026K, committed 21296K, reserved 1069056K  class space used 2259K, capacity 2324K, committed 2432K, reserved 1048576K`  `2019-07-02T16:33:10.246+0800: 11.197: [Full GC (Metadata GC Threshold) 56716K->55206K(1267200K), 0.1005246 secs]` 

## Relevant Content  

**为什么会有metaspace**  

jdk8之前有perm这一整块内存来存klass等信息，jvm在启动的时候会根据配置来分配一块连续的内存块，但是随着动态类加载的情况越来越多，这块内存我们变得不太可控，于是metaspace出现了  

**metaspace的部分参数**  

- MaxMetaspaceSize用于设置metaspace区域的最大值，这个值可以通过mxbean中的MemoryPoolBean获取到，如果这个参数没有设置，那么就是通过mxbean拿到的最大值是-1，表示无穷大。 

- MetaspaceSize表示metaspace首次使用不够而触发FGC的阈值，只对触发起作用，原因是：垃圾搜集器内部是根据变量_capacity_until_GC来判断metaspace区域是否达到阈值的 

> **注意**：GC收集器会在发生对metaspace的回收会，会计算新的_capacity_until_GC值，以后发生FGC就跟MetaspaceSize没有关系了  

> **注意**：如果不设置MetaspaceSize，则默认的_capacity_until_GC为20M左右
