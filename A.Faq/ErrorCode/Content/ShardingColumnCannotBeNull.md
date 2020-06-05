# dble-Sharding column can't be null

## Setting

- sharding.xml部分配置如下：  

```xml
<sharingTable shadingColumn="number" ... >
...
<function name="rangeLong" class="NumberRange">
  <property name="mapFile">partition.txt</property>
  <property name="defaultNode">0</property>
</function>
```
- create table account (id int(10),number int(10) not null,name varchar(20) not null);
- insert into account (id,number,name) values (1,NULL,'aaa');

## Issue  

ERROR 1064 (HY000): Sharding column can't be null when the table in MySQL column is not null

## Resolution

- number列和name列的插入值不为NULL；
- 或者修改number列为允许插入NULL值;  
ALTER TABLE `account` MODIFY `number` VARCHAR (20);
- **注意**：上一步的前提是：  
在blacklist中开启参数alterTableAllow;

```xml
<blacklist name="bk1">
    <property name="alterTableAllow">true</property>
</blacklist>
```

并修改sharding-by-range中的拆分列，dble不允许对分片键或ER键进行alter，会造成无法分片；

```xml
<sharingTable shadingColumn="id" ... >
```

- alter列值为允许插入空值后，再将拆分列修改为原值。


## Root Cause

1.  在MySQL中执行相同insert：  
报错：ERROR 1048 (23000): Column 'number' cannot be null
2. desc查看表结构：number列和name列均定义为非空列，不允许插入空值。


| Field | Type | Null | Key | Default | Extra |
| ---- | ---- | ---- | ---- | ---- | ---- |
| id | int(10) | YES |  | NULL |  |
| number | int(10) | NO |  | NULL |  |
| name | varchar(20) | NO |  | NULL |  |

  

