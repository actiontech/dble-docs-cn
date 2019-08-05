# dble-NestLoop Parameters Lead To Temptable Exception

## Setting

- 开启了NestLoop优化，设置NestLoop值的范围为4
- 两个表join关联：student表和class表
- student表结构：id列、name列、class_name列，主键id
- class表结构：id列，class_name列、teacher_name列
- `SELECT class.teacher_name FROM student  LEFT JOIN class on student.class_name=class.class_name  WHERE student.name="张三"；`

## Issue

- com.actiontech.dble.plan.common.exception.TempTableException: temptable too much rows,[rows size is 5].

## Resolution

- 调大NestLoop中的默认参数值，如下：

| 配置名称 | 配置内容 | 默认值/单位 | 详细作用原理或应用 |
| ---- | ---- | ---- | ----|
| useJoinStrategy | 是否使用nestLoop优化 | 默认使用 | 开启之后会尝试判断join两边的where来重新调整查询SQL下发的顺序 |
| nestLoopConnSize | 临时表阈值 | 默认4 | 若临时表⾏数⼤于这两个值乘积，则报告错误 |
| nestLoopRowsSize | 临时表阈值 | 默认2000 | 若临时表⾏数⼤于这两个值乘积，则报告错误 |

- 或者关闭NestLoop，不使用其优化，如下：
  - `<property name="useJoinStrategy">false</property>`
- 为了更直观，本例提前调小参数值，引发场景复现

## Root Cause

- 使用了NestLoop优化，但是依据NestLoop规则选择出的表数据量太大，超出了NestLoop默认范围
- NestLoop优化规范
> **注意**：
> - 对于两表join时，NestLoop选择小表作为驱动表 （同样适用于多join）
> - 判断依据：是否有where条件，有where条件的作为小表
> - 当两个表都有where条件或者都没有where条件，NestLoop无法判断，不起作用
> - 在不确定哪个表为小表时，不建议开启NestLoop

- 针对本例SQL做详细说明：如下
- `SELECT class.teacher_name FROM student  LEFT JOIN class on student.class_name=class.class_name  WHERE student.name="张三"；  `

根据dble中NestLoop优化规则可知：
1. SQL中使用了student表和class表
2. where条件限制指定于student表，所以NestLoop可以根据规则选择出student表作为驱动表（小表）
3. 实际SQL中student表的数据量较大，超出了NestLoop值的范围，引发报错

## Relevant Content

**MySQL的多表连接之NestLoop介绍**

1. NestLoop:
 - 对于被连接的数据子集较小的情况，Nested Loop是个较好的选择。
 - Nested Loop就是扫描一个表（外表），每读到一条记录，就根据Join字段上的索引去另一张表（内表）里面查找，若Join字段上没有索引查询优化器一般就不会选择 Nested Loop。
 - 在Nested Loop中，内表（一般是带索引的大表）被外表（也叫“驱动表”，一般为小表——不紧相对其它表为小表，而且记录数的绝对值也较小，不要求有索引）驱动，外表返回的每一行都要在内表中检索找到与它匹配的行，因此整个查询返回的结果集不能太大。

2. NestLoop优缺点

| 类别 | 使用条件 | 相关资源 | 特点 | 缺点 |
| ---- | ---- | ---- | ---- | ---- |
| NestLoop | 任何条件 | CPU、磁盘I/O | 当有高选择性索引或进行限制性搜索时效率比较高，能够快速返回第一次的搜索结果。| 当索引丢失或者查询条件限制不够时，效率很低；当表记录数多时，效率低。 |
