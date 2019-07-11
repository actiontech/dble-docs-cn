# dble-NestLoop Parameters Lead To Temptable Exception 

## Issue

driverdb.new high worth client daily a 表的数据量很大，执行报错

 - SELECT * 
FROM driverdb.new high worth client daily a
LEFT JOIN confdb.rb_branch_info b ON a.branch_no = b.branch_code
LEFT JOIN  crmdb.au_t_user c ON a.broker_id = c.user_id
LEFT JOIN crmdb.au_t_user d ON a.wealth_manager_id = d.user_id 
WHERE a.dw_trade_date = 20190221
ORDER BY a.client_id;

	错误代码：1003
Backend connect Error，Connection{DataHost[18.209.6.42:3316],Schema[driverdb]} refused

	dble日志：
com.actiontech.dble.plan.common.exception.TempTableException:temptable too much rows,[rows size is 8001]

driverdb.`arp_client_daily` a 表的数据量较小，执行成功

- SELECT * FROM driverdb.`arp_client_daily` a
LEFT JOIN confdb.tb_branch_info b ON a.`branch_no` = b.`branch_code`;

## Resolution  

1. 调大NestLoop，如下：

| 模块 | 配置名称 | 配置内容 | 默认值/单位 | 详细作用原理或应用 |
| ---- | ---- | ---- | ----| ---- |
| 使用NestLoop优化 | useJoinStrategy | 是否使用nestloop优化 | 默认不使用 | 开启之后会尝试判断join两边的where来重新调整查询SQL下发的顺序 |
| nestLoopConnSize | 临时表阈值 | 默认4 | 
| nestLoopRowsSize | 临时表阈值 | 默认2000 | 若临时表⾏数⼤于这两个值乘积，则报告错误 |

2. 关闭NestLoop，不使用其优化 
`<property name="useJoinStrategy">false</property>`

## Root Cause  

1. 使用了NestLoop优化，但是数据量太大（近百万），超出了NestLoop默认范围
2. 在不确定哪个表为小表的时候，不建议开启NestLoop
> **注意**：
> 1. 对于两表join时，NestLoop选择小表作为驱动表 （同样适用于多join）
> 2. 判断依据：是否有where条件，有where条件的作为小表 
> 3. 当两个表都有where条件或者都没有where条件，NestLoop无法判断，不起作用
 
## Relevant Content  

**NestLoop相关介绍**  

1. Nested Loop:  
- 对于被连接的数据子集较小的情况，Nested Loop是个较好的选择。
- Nested Loop就是扫描一个表（外表），每读到一条记录，就根据Join字段上的索引去另一张表（内表）里面查找，若Join字段上没有索引查询优化器一般就不会选择 Nested Loop。
- 在Nested Loop中，内表（一般是带索引的大表）被外表（也叫“驱动表”，一般为小表——不紧相对其它表为小表，而且记录数的绝对值也较小，不要求有索引）驱动，外表返回的每一行都要在内表中检索找到与它匹配的行，因此整个查询返回的结果集不能太大。

2. NestLoop优缺点

| 类别| 使用条件 | 相关资源 | 特点 | 缺点 |
| ---- | ---- | ---- | ----| ---- |
| NestLoop | 任何条件 | CPU、磁盘I/O | 当有高选择性索引或进行限制性搜索时效率比较高，能够快速返回第一次的搜索结果。 | 当索引丢失或者查询条件限制不够时，效率很低；当表记录数多时，效率低。 |
