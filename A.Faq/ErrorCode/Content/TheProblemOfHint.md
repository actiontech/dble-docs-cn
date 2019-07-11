# dble-TheProblemOfHint  

## Setting  

- mysql> /*#dble:sql=select 1 from rp_cre_data_mobile_track_cmcc */ call update_track(); 
- 预期：表为分片表，sql语句应该下发到所有节点 
- 结果：只有一个节点在执行 

## Issue  

- mysql> show @@processlist; 

| Front_Id | Datanode | BconnID | user | Front_Host | db | Command | Time | State | Info |  
| ---- | ---- | ---- | ----| ---- | ----| ---- | ----|---- | ----|
| 33 | NULL | NULL | root | 略 | NULL | NULL | 0 | updating | NULL |  
| 34 | NULL | NULL | root | 略 | NULL | NULL | 0 | updating | NULL |  
| 35 | NULL | NULL | root | 略 | NULL | NULL | 0 | updating | NULL |  
| 41 | dn9 | 9372 | root | 略 | db9 | Query | 0 | updating | NULL |  
| 42 | NULL | NULL | root | 略 | NULL | NULL | 0 | updating | NULL |  
| 43 | NULL | NULL | root | 略 | NULL | NULL | 0 | updating | NULL |  
| 30 | NULL | NULL | admin | 略 | NULL | NULL | 0 | updating | NULL | 

## Resolution  

1. 将注解方式：  /*#dble:type=....*/  
改为：  /*!dble:type=....*/ 
2. 或者在mysql client端，加上 -c 选项

>**注意**：mysql --help  
>-c, --comments  
>Preserve comments. Send comments to the server. The default is --skip-comments (discard comments), enable with --comments. 

## Root Cause  

1. /*#dble:type=....*/ 这种注释方式是mysql的标准注释 
2. 如果不加 -c 选项，默认注释会被skip 

## Relevant Content  

**hint作用**  

1. 指定路由，比如强制读写分离。 
2. 帮助dble支持一些不能实现的语句，如单节点内存储过程的创建和调用，如insert…select…； 

**Hint语法**  

Hint语法有两种形式：  
1. /*!dble:type=....*/ 
2. /*#dble:type=...*/ 

type有3种值可选：datanode，db_type，sql。  
type详情请见： https://actiontech.github.io/dble-docs-cn/2.Function/2.04_hint.html  

**Hint注意事项**  

- 使用select语句作为注解SQL，不要使用delete/update/insert 等语句。delete/update/insert 等语句虽然也能用在注解中，但这些语句在SQL处理中有一些额外的逻辑判断，会降低性能，不建议使用
- 注解SQL 禁用表关联语句
- 使用hint做DDL需要额外执行reload @@metadata 
- 使用hint做session级别的系统变量和环境变量可能不会生效，请慎用 
- dble的注解和MySQL原生注解含义不同, 想通过MySQL原生注解来设置变量或者指定索引是无法得到预期结果的。如#1169 
- 使用注解并不额外增加的执行时间；从解析复杂度以及性能考虑，注解SQL应尽量用最简单的SQL 语句，如select id from tab_a where id=’10000’； 
- 能不用注解也能够解决的场景，尽量不用注解
