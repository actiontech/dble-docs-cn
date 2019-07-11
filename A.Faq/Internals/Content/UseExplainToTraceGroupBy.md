# dble-How To Use Explain To Resolve The Distribution Rules Of Group Gy

## Questions  

一张数据表做了分表。
如果查询里有group by分组统计，运行原理是按范围去各分表查询出数据后，再到中间件里进行分组统计的吗？ 

## Conclusions

 - 会在中间件中做数据重聚合
1. 利用explain工具查看sql的执行过程
2. dble 在explain上做了大量改善，相比mycat能提供更详实的执行计划，更准确的反映SQL语句的执行过程

## For Example

1. 配置好配置文件：
  
  schema.xml：
 `<table name="eee" dataNode="dn1,dn2" primaryKey="id" rule="sharding-by-hash"/>`
 
 rule.xml：
 `<tableRule name="sharding-by-hash">
        <rule>
            <columns>id</columns>
            <algorithm>hashLong</algorithm>
        </rule>
    </tableRule>`
    
`<function name="hashLong" class="Hash">
        <property name="partitionCount">2</property>
        <property name="partitionLength">128</property>
    </function>`

2. 在dble client创建表 eee 并插入数据：
mysql> select * from eee;

	| id | name |
	| -- | -- |
	|    1 | 上海   |
	|    2 | 广州   |
	|    3 | 杭州   |
	|    4 | 北京   |
	|    5 | 北京   |
	|  130 | 北京   |
	|  131 | 北京   |
	|  132 | 上海   |
	|  133 | 上海   |
	|  134 | 上海   |

mysql> select name,count(name) from eee group by name;

| name | COUNT(name) |
| -- | -- |
| 上海 | 4 |
| 北京 | 4 |
| 广州 | 1 |
| 杭州 | 1 |

3. 利用explain工具查看sql的执行过程

| DATA_NOD | TYPE | SQL/REF |
| -- | -- | -- |
| dn1_0 | BASE SQL| select `eee`.`name`,COUNT(name) as `_$COUNT$_rpda_0` from  `eee` GROUP BY `eee`.`name` ASC |
| dn2_0 | BASE SQL| select `eee`.`name`,COUNT(name) as `_$COUNT$_rpda_0` from  `eee` GROUP BY `eee`.`name` ASC |
| merge_1 | MERGE| dn1_0; dn2_0 |
| aggregate_1| AGGREGATE| merge_1 |
| shuffle_field_1| SHUFFLE_FIELD| aggregate_1 |

## Instructions

由explain的结果可知：
1. dble将sql语句下发到对应datanode执行
2. 将对应datanode数据结果进行merge
3. 对merge后的数据进行group by聚合
4. SHUFFLE_FIELD进行整理，达到用户预期的结果
> **注意**：普通用户可以不关注SHUFFLE_FIELD

## Relevant Content 

**dble的内部功能层**

1. 在dble内部，包括了三个部分：面向app的连接层，内部功能层，面向myslq的连接池。
2. 内部功能层实现：前端请求接收，处理过后由后端协议层发出，将数据返回给用户。
	
	内部功能涉及到了简单查询和复杂查询：
	- 简单查询：直接下发单个/多个节点
	- 复杂查询：dble内部需要进行排序、聚合、join、group by，结果集计算
	- 详细介绍：[https://opensource.actionsky.com/dble-lesson-one/](https://opensource.actionsky.com/dble-lesson-one/)
	
**dble内部各线程池简介**

1. 后端IO接收线程
处理来自MySQL连接的网络包（sql的执行结果、查询的结果集）。

2. 后端业务处理线程
简单查询的后端MySQL返回处理，并将结果转发反馈给客户端。

3. 复杂查询处理线程
处理复杂查询MySQL返回结果，包括结果集的聚合，对比，排序，去重，子查询语句下发等。
> PS：dble中仅此线程池不限线程数量
