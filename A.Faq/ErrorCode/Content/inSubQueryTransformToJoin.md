# inSubQueryTransformToJoin
## Issue
[链接](https://github.com/actiontech/dble/issues/2130)  

### example  

* 执行sql  
  explain select a.* from gtest a where 1=1 and a.id in (select b.id from test b) order by a.id;  
* inSubQueryTransformToJoin = true 时in子查询执行计划    
  
```
+----------------------------+--------------------------+-----------------------------------------------------------------------------------------------+
| SHARDING_NODE              | TYPE                     | SQL/REF                                                                                       |
+----------------------------+--------------------------+-----------------------------------------------------------------------------------------------+
| dn1_0                      | BASE SQL                 | select `a`.`id`,`a`.`name` from  `gtest` `a` ORDER BY `a`.`id` ASC                            |
| dn2_0                      | BASE SQL                 | select `a`.`id`,`a`.`name` from  `gtest` `a` ORDER BY `a`.`id` ASC                            |
| merge_and_order_1          | MERGE_AND_ORDER          | dn1_0; dn2_0                                                                                  |
| shuffle_field_1            | SHUFFLE_FIELD            | merge_and_order_1                                                                             |
| dn1_1                      | BASE SQL                 | select DISTINCT `b`.`id` as `autoalias_scalar` from  `test` `b` ORDER BY autoalias_scalar ASC |
| dn2_1                      | BASE SQL                 | select DISTINCT `b`.`id` as `autoalias_scalar` from  `test` `b` ORDER BY autoalias_scalar ASC |
| merge_and_order_2          | MERGE_AND_ORDER          | dn1_1; dn2_1                                                                                  |
| distinct_1                 | DISTINCT                 | merge_and_order_2                                                                             |
| shuffle_field_3            | SHUFFLE_FIELD            | distinct_1                                                                                    |
| rename_derived_sub_query_1 | RENAME_DERIVED_SUB_QUERY | shuffle_field_3                                                                               |
| shuffle_field_4            | SHUFFLE_FIELD            | rename_derived_sub_query_1                                                                    |
| join_1                     | JOIN                     | shuffle_field_1; shuffle_field_4                                                              |
| shuffle_field_2            | SHUFFLE_FIELD            | join_1                                                                                        |
+----------------------------+--------------------------+-----------------------------------------------------------------------------------------------+
```
* inSubQueryTransformToJoin = false 时in子查询执行计划  

```
+-------------------+-----------------------+----------------------------------------------------------------------------------------------------------------------------+
| SHARDING_NODE     | TYPE                  | SQL/REF                                                                                                                    |
+-------------------+-----------------------+----------------------------------------------------------------------------------------------------------------------------+
| dn1_0             | BASE SQL              | select DISTINCT `b`.`id` as `autoalias_scalar` from  `test` `b`                                                            |
| dn2_0             | BASE SQL              | select DISTINCT `b`.`id` as `autoalias_scalar` from  `test` `b`                                                            |
| merge_1           | MERGE                 | dn1_0; dn2_0                                                                                                               |
| distinct_1        | DISTINCT              | merge_1                                                                                                                    |
| shuffle_field_1   | SHUFFLE_FIELD         | distinct_1                                                                                                                 |
| in_sub_query_1    | IN_SUB_QUERY          | shuffle_field_1                                                                                                            |
| dn1_1             | BASE SQL(May No Need) | in_sub_query_1; select `a`.`id`,`a`.`name` from  `gtest` `a` where `a`.`id` in ('{NEED_TO_REPLACE}') ORDER BY `a`.`id` ASC |
| dn2_1             | BASE SQL(May No Need) | in_sub_query_1; select `a`.`id`,`a`.`name` from  `gtest` `a` where `a`.`id` in ('{NEED_TO_REPLACE}') ORDER BY `a`.`id` ASC |
| merge_and_order_1 | MERGE_AND_ORDER       | dn1_1; dn2_1                                                                                                               |
| shuffle_field_2   | SHUFFLE_FIELD         | merge_and_order_1                                                                                                          |
+-------------------+-----------------------+----------------------------------------------------------------------------------------------------------------------------+
```
## Resolution
前置条件： test表数据量少称为小表，gtest表数据量多称为大表。 子查询select b.id from test b简称为 subQuery. 整条sql select a.* from gtest a where 1=1 and a.id in (select b.id from test b) order by a.id;
简称为sql.  
正常使用in子查询中应该有意识的会把小表的查询结果当成条件放在大表中查询。在inSubQueryTransformToJoin = true的执行中会把多张表的数据都查出来然后再做join处理，这样处理方式可能并不符合预期使用子查询写法的目的（多查询了mysql中的数据）。
在inSubQueryTransformToJoin = false的执行中会先处理subQuery，然后sql会带上subQuery的结果去mysql中查询，而非之前的对两张表做join处理。如果有嵌套子查询的情况，会先处理最里层的subQuery然后递归处理外面一层直至最外面一层。  

## conditions
* Column中包含子查询  
* join时候包含子查询  
* having中包含子查询  
* order by 包含等值子查询  
* where 后面包含子查询  
* 子查询中嵌套子查询  
* in子查询必须出现在where中才会被dble进行处理  

## explain comparison  

* 子查询有三种形式，scalar_sub_query, in_sub_query, all_any_sub_query，出现于 SQL/REF中。只有处理 in_sub_query形式会出现执行计划不一致的情况。  

### example  
#### sql  
SELECT a.id, select max(b.id) from test b where b.id in (select distinct d.id from sing1 d) as name FROM sharding_4_t1 a ORDER BY a.id;  
#### step  
* 这条sql有子查询，并且子查询出现在Column处，那么这条子查询需要处理。   
* in子查询出现在where条件中那么执行计划不一致。   

### special  
any, some ,all函数可能会在dble中当成in子查询处理。  
* any ，some 函数，并且函数的前置操作符是 =，会当成in子查询处理。      
* all函数，并且函数的前置操作符是!=，<>， 会被当成in子查询处理。  

#### example  
等价于用in子查询写法。  
* select * from sharding_4_t1 where id=any(select id from test where age=1) order by name desc;   
* select * from sharding_4_t1 where id!=all(select id from test where age=1) order by name desc;  

不等价于用in子查询写法。  
* select * from sharding_4_t1 where id!=any(select id from test where age=1) order by name desc;  
* select * from sharding_4_t1 where id=all(select id from test where age=1) order by name desc;  

所以在以上特殊函数的特殊情况会当成in子查询进行处理。  
