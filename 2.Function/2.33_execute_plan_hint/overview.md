# 2.23 通过hint指定复杂查询执行计划

## 需求背景

有如下场景1:
```sql
table_a a left join table_b b on a.col_1 = b.col_1 left join table_c c on a.col_2 =c.col_2 where a.col =xxx
```
在3.22.01.0之前版本的查询计划是:
1. a 表带条件 a.col=xxx 下发，结果集比较小，大约数百
2. b 表大表，全数据拉取
3. c 表小表，全数据拉取

三表并发下发，在dble内存中进行join，其中 b 表比较大，占用内存比较大，这样造成这条sql的执行效率不高，并且dble容易内存溢出。
因此，期望如下的查询计划:
1. a 表带条件下发，结果集大约数百
2. b 表带着 a 表的结果下发
3. c 表带着 a 表的结果下发

这样，a 表先下发，之后 b 表带上 a 表查询回来的 col_1 的结果下发，c 表带着 a 表查询回来的 col_2 的结果下发，这里，b 表和 c表的是可以并发下发的。最终将结果在dble内部进行join，这样dble处理的结果集就小很多。

有如下场景2:
```sql
table_a a left join table_b b on a.col_1 = b.col_1 left join table_c c on a.sharding_col = c.sharding_col where a.col =xxx
```
同场景1的处理方式。
因此，期望如下的查询计划:
1. a，c 表优先进行联表查询处理，带条件下发，结果集大约数百
2. b 表带着 a 表查询返回的 col_1 的结果下发

有如下场景3:
```sql
table_a a left join table_b b on a.col_1 = b.col_1 left join table_c c on b.col_2 = c.col_2 where a.col =xxx
```
同场景1的查询计划。

因此，期望如下的查询计划:
1. a 表带条件下发，结果集大约数百
2. b 表带着 a 表的结果下发
3. c 表带着 b 表的结果下发

这样，a 表先处理，然后 b 表带着 a 表 col_1 结果下发，最后 c 表带着 b 表 col_2的值下发。

另外，还可以有如下的查询计划:
1. a 表带条件下发，结果集大约数百
2. b 表带着a表的结果下发
3. c 表数据量不大的情形下全量下发

这样，a 表先处理，然后 b 表带着a 表 col_1 的结果下发，同时 c 表并发

## hint语法
针对上面三种场景，dble不能估算数据量的大小，按照表达式运算来尽量优化下发顺序。在dble 3.22.01.0版本中，dble提供通过hint的方式让用户可以自定义合理的执行顺序。

hint 的语法沿用 [dble hint](../2.04_hint.md)
比如：
```sql
/*!dble:plan=a & ( b | c )$left2inner$right2inner$in2join$use_table_index*/ sql
```
其中关键点在于 a & ( b | c ) 表达式，其中a，b，c 表示 sql 中的 **表的别名**

我们使用 &，| 表示两表操作的先后顺序。
针对上面的不同场景可以使用如下表达式指定复杂查询的执行顺序：
* 对于场景1: a & ( b | c )
* 对于场景2: (a,c) & b
* 对于场景3: 第一种小场景可以是：a&b&c ，第二种小场景可以是(a & b) | c

其中：
1. (a,c) 表示a和c表之间存在ER关系，可以整体下推
2. & 表示后面的内容依赖前面的内容，需要等待前面的结果返回之后带入到后面之中作为条件下发，相当于nestloop的方式
3. | 表示两者可以并发，数据处理方式取决于join的方式
4. left2inner 参数表示是将left join转成inner join
5. right2inner 参数表示是将right join转成inner join
6. in2join 参数表示将in子查询转为join查询；（此参数优先于bootstrap.cnf中的inSubQueryTransformToJoin策略）

在实际使用中，sql中的表别名通常是由框架生成，不易获取。
dble提供 use_table_index 参数，使用该参数可以通过sql中表的序列号来表示表的别名。
比如：
```sql
/*!dble:plan=1 & 2 & 3 )$use_table_index*/ select * from t1 a left join t2 b on a.id = b.id left join t3 c on a.id=c.id
```
这样的话，1 就表示 a，2 表示 b，3 表示 c。1，2，3表示 sql 中的 **表的别名序列号**

#### hint使用nestLoop的原则  
- hint期望的下发结果，如果违背优化的初衷那么就会报错  
举例： a join b ,如果a,b具有er关系，hint希望执行为（a & b）,那么就会报错  
- hint期望的下发方式被判定为不合理就会报错  
举例： a join b on a.col1 = b.col1 join c on c.col2 = a.col2, hint希望执行为 ( a & b & c), 那么就会报错  



## 限制
1. 执行计划中hint语法支持的不够完善，在有括号的场景下会解析错误。
2. 对于像 Hibernate 这样自动生成表别名的框架，当前还不支持。后续会优化。
3. 当 sql 存在笛卡尔积（join 不指定关联key） 时，暂不支持，hint会报错。举例：`select * from table_a a, table_b b ` 
4. 当 sql 存在 多个 right join 时，暂不支持，hint会报错
5. 当 sql 存在 子查询 时，暂不支持，hint会报错
6. left join 和 inner join指向同一个节点的执行顺序不被允许，会报错。举例：`/*!dble:plan=a & c & b */ SELECT * FROM Employee a LEFT JOIN Dept b on a.name=b.manager inner JOIN Info c on a.name=c.name and b.manager=c.name ORDER BY a.name;` 其中，a 和 c 可以正常 inner join ,但其结果和 b 发生join 时，需要同时完成 a 和 b 的 left join以及 c 和 b 的inner join，这在sql语法上不受支持，故不支持。
7. sql具有er关系，但是hint依旧下发成功。  
原因：我们尽可能的按照hint期望的方式下发语句，所以dble可能尝试在内部改写sql以便满足hint的需求，举例`/*!dble:plan=a | c | b */ SELECT a.Name,a.DeptName,b.Manager,c.salary FROM Employee a LEFT JOIN Dept b on a.DeptName=b.DeptName LEFT JOIN Level c on a.Level=c.levelname and c.salary=10000 order by a.Name ;`
会被调整为`SELECT a.Name,a.DeptName,b.Manager,c.salary FROM Employee a  LEFT JOIN Level c on a.Level=c.levelname and c.salary=10000 LEFT JOIN Dept b on a.DeptName=b.DeptName  order by a.Name`,此时a表和c表不具有er关系，且er关系的检测不能跨节点，所以没有违背hint使用nestLoop的原则的第一条,可以正常下发
