# dble-OutOfMemoryError 

## Setting  

load data语句，一共有17G的数据，每条语句4K  
load data相关参数值均为默认 

## Issue
 - INFO | jvm 1 | 2019/06/28 14:55:37 | Exception in thread "backendBusinessExecutor17" java.lang.OutOfMemoryError: GC overhead limit exceeded. 

## Resolution  

1. 调小maxRowSizeToFile参数值，减少占用内存量

| 配置名称 | 配置内容 | 默认值 | 详细作用原理或应用 | 
| ---- | ---- | ---- | ----|
| maxCharsPerColumn | 每列所允许最⼤字符数 | 默认为65535 | 每⾏所允许最⼤字符数 |  
| maxRowSizeToFile | 需要持久化的最⼤⾏数 | 默认为10000 | 当load data的数据⾏数超过阈值后，会将数据保存在⽂件中以防OOM | 

2. 调大wrapper.conf中的Xmx值 
> **注意**：发生OOM，可以关注一下这个值的大小是否配置合适 

## Root Cause  

1. maxRowSizeToFile参数设置过大，对于机器来说超出了承载的数据量，导致内存溢出 
2. 配置文件中Xmx设置过小，内存不够 
>**注意**：On-Heap ⼤⼩由JVM 参数Xms ,Xmx 决定，就是正常服务需要的内存，由jvm⾃动分配和回收。 

## Relevant Content  

**load data相关配置**  

设置load data相关参数时，不要过度的调大，防止OOM  

**JVM配置**  

以下为建议值：  
1. dble总内存=0.6 可⽤物理内存(刨除操作系统,驱动等的占⽤) 
2. Xmx = 0.4 dble总内存 
3. MaxDirectMemorySize = 0.6 * dble总内存 

**堆内存分配**  

1. JVM初始分配的内存由-Xms指定，默认是物理内存的1/64； 
2. JVM最大分配的内存由-Xmx指定，默认是物理内存的1/4。 
3. 默认空余堆内存小于40%时，JVM就会增大堆直到-Xmx的最大限制；空余堆内存大于70%时，JVM会减少堆直到-Xms的最小限制。
