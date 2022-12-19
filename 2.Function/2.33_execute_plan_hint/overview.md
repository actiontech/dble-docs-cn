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

## hint使用场景举例

数据插入（jdbc方式）

```aidl
import java.sql.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class jdbctest {
    static AtomicInteger index = new AtomicInteger(0);
    static volatile Connection conn = null;
    private static List<Connection> list = new ArrayList<>();
    private static void createConn(String username, String password) {
        String JDBC_DRIVER = "com.mysql.jdbc.Driver";
        String url = "jdbc:mysql://127.0.0.1:8066/test1?useSSL=false";
        try {
            // 注册 JDBC 驱动
            Class.forName(JDBC_DRIVER);
            conn = DriverManager.getConnection(url, username, password);
            list.add(conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void createTable() {
        Statement stmt;
        try {
            // 注册 JDBC 驱动
            Connection conn = list.get(index.incrementAndGet());
            stmt = conn.createStatement();
            stmt.addBatch("drop table if EXISTS t_spec_group ;");
            stmt.addBatch("drop table if EXISTS t_spu ;");
            stmt.addBatch("drop table if EXISTS t_sku ;");
            stmt.addBatch("drop table if EXISTS t_warehouse_sku ;");
            stmt.executeBatch();
            stmt.addBatch("create table t_spec_group(" +
                    "    id     int unsigned primary key  comment '主键'," +
                    "    spg_id int unsigned not null comment '品类ID'," +
                    "    `type` varchar(200) not null comment '品类类型'," +
                    "    `name` varchar(200) not null comment '品类名称'" +
                    ") comment ='详细品类表';");
            stmt.addBatch("create table t_spu(" +
                    "    id               int unsigned primary key  comment '主键'," +
                    "    title            varchar(200) not null comment '标题'," +
                    "    category_id      int unsigned not null comment '产品ID'," +
                    "    saleable         int unsigned not null comment '是否上架', " +
                    "    spg_id           int unsigned comment '品类ID'" +
                    ") comment ='产品表';");
            stmt.addBatch("create table t_sku(" +
                    "    id               int unsigned primary key  comment '主键'," +
                    "    spu_id           int unsigned not null comment '商品ID'," +
                    "    spg_id           int unsigned not null comment '品类ID'," +
                    "    title            varchar(200) not null comment '标题'," +
                    "    price            int unsigned not null comment '价格'" +
                    ") comment ='商品表';");
            stmt.addBatch("create table t_warehouse_sku(" +
                    "    warehouse_id int unsigned comment '主键'," +
                    "    sku_id       int unsigned comment '商品ID'," +
                    "    spg_id       int unsigned not null comment '品类ID'," +
                    "    title        varchar(200) not null comment '标题'," +
                    "    type         varchar(200) comment '品类类型'," +
                    "    num          int unsigned not null comment '库存数量'" +
                    ") comment '仓库商品库存表';");
            stmt.executeBatch();
            stmt.clearBatch();
            System.out.println("-------end------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertSpec_group() {
        PreparedStatement ps = null;
        try {
            Connection conn = list.get(index.incrementAndGet());
            String sql = "INSERT INTO t_spec_group (id, spg_id,type, name) VALUES (?,?,?,?);";
            ps = conn.prepareStatement(sql);
            int size = 300;
            for (int i = 0; i < size; i++) {

                if (i < 200) {
                    ps.setInt(1, i);
                    ps.setInt(2, i + 2000000);
                    ps.setString(3, "phone");
                    ps.setString(4, "iphone" + i);
                } else {
                    ps.setInt(1, i);
                    ps.setInt(2, i);
                    ps.setString(3, "desk" + i);
                    ps.setString(4, "idesk" + i);
                }
                ps.addBatch();
                if (i % 500 == 0) {
                    // 执行批量更新
                    ps.executeBatch();
                    // 清空执行过的sql
                    ps.clearBatch();
                }
            }
            ps.executeBatch();
            // 清空执行过的sql
            ps.clearBatch();
            System.out.println("-------insertSpec_group---end------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertT_spu() {
        PreparedStatement ps = null;
        try {
            Connection conn = list.get(index.incrementAndGet());
            String sql = "INSERT INTO t_spu (id, title, category_id,saleable,spg_id) VALUES (?, ?, ?, ?, ?);";
            ps = conn.prepareStatement(sql);
            int size = 1000000;
            for (int i = 0; i < size; i++) {
                ps.setInt(1, i);
                if (i < 200) {
                    ps.setString(2, "this is phone");
                    ps.setInt(5, i + 2000000);
                } else {
                    ps.setString(2, "this is desk" + i);
                    ps.setInt(5, i);

                }
                ps.setInt(3, i);
                ps.setInt(4, 1);

                ps.addBatch();
                if (i % 500 == 0) {
                    // 执行批量更新
                    ps.executeBatch();
                    // 清空执行过的sql
                    ps.clearBatch();
                }
            }
            ps.executeBatch();
            // 清空执行过的sql
            ps.clearBatch();
            System.out.println("-------insertT_spu---end------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertT_sku() {
        PreparedStatement ps = null;
        try {
            Connection conn = list.get(index.incrementAndGet());
            String sql = "INSERT INTO t_sku (id, spu_id,spg_id,title,price) VALUES (?, ?, ?,?, ?);";
            ps = conn.prepareStatement(sql);
            int size = 1000000;
            for (int i = 0; i < size; i++) {
                ps.setInt(1, i);
                ps.setInt(2, i);

                if (i < 200) {
                    ps.setInt(3, i + 2000000);
                    ps.setString(4, "iphone" + i);
                } else {
                    ps.setInt(3, i);
                    ps.setString(4, "idesk" + i);
                }
                ps.setInt(5, new Random().nextInt(2000));
                ps.addBatch();
                if (i % 500 == 0) {
                    // 执行批量更新
                    ps.executeBatch();
                    // 清空执行过的sql
                    ps.clearBatch();
                }
            }
            ps.executeBatch();
            // 清空执行过的sql
            ps.clearBatch();
            System.out.println("------- insertT_sku---end------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertT_warehouse_sku() {
        PreparedStatement ps = null;
        try {
            Connection conn = list.get(index.incrementAndGet());
            String sql = "INSERT INTO t_warehouse_sku (warehouse_id, sku_id,spg_id, title,type, num) VALUES (?, ?,?,?,?, ?);";
            ps = conn.prepareStatement(sql);
            int size = 1000000;
            for (int i = 0; i < size; i++) {
                ps.setInt(1, i);
                ps.setInt(2, i);
                if(i < 200){

                    ps.setInt(3, i + 2000000);
                    ps.setString(4, "iphone" + i);
                    ps.setString(5, "phone");
                }else {
                    ps.setInt(3, i);
                    ps.setString(4, "idesk" + i);
                    ps.setString(5, "desk");
                }
                ps.setInt(6, new Random().nextInt(200));
                ps.addBatch();
                if (i % 500 == 0) {
                    // 执行批量更新
                    ps.executeBatch();
                    // 清空执行过的sql
                    ps.clearBatch();
                }
            }
            ps.executeBatch();
            // 清空执行过的sql
            ps.clearBatch();
            System.out.println("-------insertT_warehouse_sku---end------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws InterruptedException {
        int size = 6;
        //需要改成user.xml中配置的用户名和密码
        String username = "aa";
        String password = "123456";
        ThreadPoolExecutor executor = new ThreadPoolExecutor(size, size, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        for (int i = 0; i < size; i++) {
            createConn(username, password);
        }
         createTable();
        executor.execute(() -> insertSpec_group());
        executor.execute(() -> insertT_warehouse_sku());
        executor.execute(() -> insertT_sku());
        executor.execute(() -> insertT_spu());
    }
}

```

sharding.xml

```
<?xml version="1.0"?>
<!DOCTYPE dble:sharding SYSTEM "sharding.dtd">
<dble:sharding xmlns:dble="http://dble.cloud/">
    <schema name="test1" >
        <shardingTable name="t_spec_group" shardingNode="dn1,dn2" function="sql-mod" shardingColumn="id"></shardingTable>
        <shardingTable name="t_spu" shardingNode="dn1,dn2" function="sql-mod" shardingColumn="id"></shardingTable>
        <shardingTable name="t_sku" shardingNode="dn1,dn2" function="hash-string-into-two" shardingColumn="title"></shardingTable>
        <shardingTable name="t_warehouse_sku" shardingNode="dn1,dn2" function="hash-string-into-two" shardingColumn="title"></shardingTable>
    </schema>

    <shardingNode dbGroup="dbGroup1" database="db1" name="dn1"/>
    <shardingNode dbGroup="dbGroup2" database="db1" name="dn2"/>
    <shardingNode dbGroup="dbGroup3" database="db1" name="dn3"/>
    <shardingNode dbGroup="dbGroup4" database="db1" name="dn4"/>

    <function name="hash-string-into-two" class="StringHash">
        <property name="partitionCount">2</property>
        <property name="partitionLength">1</property>
    </function>

    <function name="sql-mod" class="Hash">
        <property name="partitionCount">2</property>
        <property name="partitionLength">1</property>
    </function>


</dble:sharding>
```

db.xml

```aidl
<?xml version="1.0"?>
<!--
  ~ Copyright (C) 2016-2020 ActionTech.
  ~ License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher.
  -->
<!DOCTYPE dble:db SYSTEM "db.dtd">
<dble:db xmlns:dble="http://dble.cloud/" version="4.0">
    <dbGroup name="dbGroup1" rwSplitMode="0" delayThreshold="100" >
        <heartbeat timeout="30" >show slave status</heartbeat>
        <dbInstance name="M1" url="ip1:3306" user="root" password="123456" maxCon="300" minCon="10" id="100"
                    primary="true" >
        </dbInstance>
    </dbGroup>

    <dbGroup name="dbGroup2" rwSplitMode="0" delayThreshold="100">
        <heartbeat>show slave status</heartbeat>
        <dbInstance name="M2" url="ip2:3306" user="root" password="123456" id="1" maxCon="2000" minCon="10"
                    primary="true">
        </dbInstance>
    </dbGroup>

        <dbGroup name="dbGroup3" rwSplitMode="0" delayThreshold="100">
            <heartbeat errorRetryCount="1" timeout="10">show slave status</heartbeat>
            <dbInstance name="M3" url="ip3:3306" user="root" password="123456" id="1" maxCon="2000" minCon="10"
                        primary="true">
            </dbInstance>
        </dbGroup>

    <dbGroup name="dbGroup4" rwSplitMode="2" delayThreshold="100">
        <heartbeat errorRetryCount="1" timeout="10">show slave status</heartbeat>
        <dbInstance name="M4" user="root" password="123456" url="ip4:3306" maxCon="20" minCon="10"
                    primary="true">
        </dbInstance>
    </dbGroup>
</dble:db>
```

### 带有where条件
场景一   
```
select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on b.spg_id=c.spg_id where a.type = 'phone';
```

使用hint前（并发下发）
1. a表带有条件下发，结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表直接下发，全数据拉取结果集为百万

使用hint后  
```
/*!dble:plan=a & b & c */ select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on b.spg_id=c.spg_id where a.type = 'phone';
```

hint语法 a & b & c
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百
3. c表带着a表和b表的结果下发，结果集约为数百

结论：推荐使用hint写法

场景二   
```
select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone';
```

使用hint前（并发下发）
1. a表带有条件下发，结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表直接下发，全数据拉取结果集为百万

使用hint后 
```
/*!dble:plan=a & b & c */ select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone';
```

hint语法 a & b & c  
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百
3. c表带着a表和b表的结果下发，结果集约为数百

结论：推荐使用hint写法

使用hint后  
```
/*!dble:plan=a & (b | c) */ select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone';
```

hint语法a & (b | c)
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百，c表带着a表的结果下发，结果集约为数百

结论：当a表的结果集较小且b表和c表的结果集较大，更推荐该hint写法

场景三  
```
select  *  from t_warehouse_sku a inner join t_sku b on a.sku_id = b.id inner join t_spec_group c on b.spg_id=c.spg_id where a.type = 'phone';
```

使用hint前（并发下发）
1. a表带有条件下发，结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表直接下发，全数据拉取结果集约为数百

使用hint后  
```
/*!dble:plan=a & b & c */ select  *  from t_warehouse_sku a inner join t_sku b on a.sku_id = b.id inner join t_spec_group c on b.spg_id=c.spg_id where a.type = 'phone';
```

hint语法 a & b & c
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百
3. c表带着a表和b表的结果下发，结果集约为数百

结论：推荐使用hint写法

使用hint后  
```
/*!dble:plan=a & b | c */ select  *  from t_warehouse_sku a inner join t_sku b on a.sku_id = b.id inner join t_spec_group c on b.spg_id=c.spg_id where a.type = 'phone';
```

hint语法 a & b | c
1. a表带有条件下发，结果集约为数百，c表直接下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百

结论：当a表的结果集和c表的结果集较小且b表的结果集较大，更推荐使用该hint写法

场景四  
```
select  *  from t_warehouse_sku a inner join t_sku b on a.sku_id = b.id inner join t_spec_group c on a.spg_id=c.spg_id where a.type = 'phone';
```

使用hint前（并发下发）
1. a表带有条件下发，结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表直接下发，全数据拉取结果集约为数百

使用hint后  
```
/*!dble:plan=a & b & c */ select  *  from t_warehouse_sku a inner join t_sku b on a.sku_id = b.id inner join t_spec_group c on a.spg_id=c.spg_id where a.type = 'phone';
```

hint语法 a & b & c
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百
3. c表带着a表和b表的结果下发，结果集约为数百

结论：推荐使用hint写法

使用hint后  
```
/*!dble:plan=a & (b | c) */ select  *  from t_warehouse_sku a inner join t_sku b on a.sku_id = b.id inner join t_spec_group c on a.spg_id=c.spg_id where a.type = 'phone';
```

hint语法 a & (b | c)
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百,c表带着a表的结果下发，结果集约为数百

结论：当a表的结果集较小且b表和c表的结果集较大时，更推荐使用该hint写法


使用hint后  
```
/*!dble:plan=a & b | c */ select  *  from t_warehouse_sku a inner join t_sku b on a.sku_id = b.id inner join t_spec_group c on a.spg_id=c.spg_id where a.type = 'phone';  
```

hint语法 a & b | c
1. a表带有条件下发，结果集约为数百，c表直接下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百

结论：当a表和c表的结果集较小且b表的结果集较大，更推荐使用该hint写法

场景五  
```
select  *  from t_warehouse_sku a inner join t_spu b on a.spg_id = b.spg_id inner join t_sku c on a.title=c.title where a.type = 'phone';
```

使用hint前（并发下发）
1. a表带有条件下发，结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表直接下发，全数据拉取结果集为百万

使用hint后  
```
/*!dble:plan=(a,c) & b*/ select  *  from t_warehouse_sku a inner join t_spu b on a.spg_id = b.spg_id inner join t_sku c on a.title=c.title where a.type = 'phone';
```

hint语法 (a,c) & b
1. a表和c表带有条件整体下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百

结论：推荐使用hint写法

场景六   
```
select  *  from t_warehouse_sku a inner join t_spec_group b on a.spg_id = b.spg_id inner join t_sku c on a.title=c.title where a.type = 'phone';
```

使用hint前（并发下发）
1. a表带有条件下发，结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表直接下发，全数据拉取结果集为百万

使用hint后  
```
/*!dble:plan=(a,c) & b*/ select  *  from t_warehouse_sku a inner join t_spec_group b on a.spg_id = b.spg_id inner join t_sku c on a.title=c.title where a.type = 'phone';  
```

hint语法 (a,c) & b  
1. a表和c表带有条件整体下发，结果集约为数百  
2. b表带着a表的结果下发，结果集约为数百  

结论：推荐使用hint写法  

使用hint后  
```
/*!dble:plan=(a,c) | b*/ select  *  from t_warehouse_sku a inner join t_spec_group b on a.spg_id = b.spg_id inner join t_sku c on a.title=c.title where a.type = 'phone';
```

hint语法 (a,c) | b  
1. a表和c表带有条件整体下发，结果集约为数百,b表直接结果下发，结果集约为数百

结论：当a表和c表整体下发且b表的结果集较小时，更推荐使用该hint写法

场景七  
```
select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone' and b.category_id < 200;
```

使用hint前（并发下发）
1. a表带有条件下发，结果集约为数百
2. b表带有条件下发，结果集约为数百
3. c表直接下发，全数据拉取结果集为百万

使用hint后  
```
/*!dble:plan=a & b & c */ select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone' and b.category_id < 200;
```

hint语法 a & b & c
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发和where条件下发，结果集约为数百
3. c表带着a表和b表的结果下发，结果集约为数百

结论：推荐使用hint写法

使用hint后  
```
/*!dble:plan=a & (b | c) */ select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone' and b.category_id < 200;
```

hint语法 a & (b | c)
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发和where条件下发，结果集约为数百
3. c表带着a表的结果下发，结果集约为数百

结论：当a表的结果集较小并且b表和c表的结果集较大时,更推荐使用该hint写法

场景八  
```
select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone' and c.title like 'iphone%';
```

使用hint前（并发下发）
1. a表带有条件下发，结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表带有条件下发，结果集约为数百

使用hint后   
```
/*!dble:plan=a & b & c */ select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone' and c.title like 'iphone%';
```

hint语法 a & b & c  
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百
3. c表带着a表和b表的结果和where条件下发，结果集约为数百

结论：推荐使用hint写法

使用hint后   
```
/*!dble:plan=a & (b | c) */ select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone' and c.title like 'iphone%';
```

hint语法 a & (b | c)
1. a表带有条件下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百
3. c表带着a表的结果和where条件下发，结果集约为数百

结论：当a表的结果集较小且b表和c表的结果集较大,推荐使用hint写法

使用hint后  
```
/*!dble:plan=a & b | c */ select  *  from t_warehouse_sku a left join t_spu b on a.spg_id = b.spg_id left join t_sku c on a.sku_id=c.id where a.type = 'phone' and c.title like 'iphone%';
```

hint语法 a & b | c
1. a表带有条件下发，结果集约为数百,c表带有条件下发，结果集约为数百,
2. b表带着a表的结果下发，结果集约为数百

结论：当a表和c表的结果集较小且b表的结果集较大,更推荐该hint写法

### 不带有where条件   
场景场景一   
```
select  *  from t_spec_group a inner join t_spu b on a.spg_id = b.spg_id inner join t_sku c on b.spg_id=c.spg_id;  
```

使用hint前（并发下发）
1. a表直接下发，结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表直接下发，全数据拉取结果集为百万

使用hint后  
```
/*!dble:plan=a & b & c */ select  *  from t_spec_group a inner join t_spu b on a.spg_id = b.spg_id inner join t_sku c on b.spg_id=c.spg_id;
```

hint语法 a & b & c
1. a表直接下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百
3. c表带着a表和b表的结果下发，结果集约为数百

结论：推荐使用hint写法

场景二  
```
select  *  from t_spec_group a inner join t_spu b on a.spg_id = b.spg_id inner join t_sku c on a.spg_id=c.spg_id;
```

使用hint前（并发下发）
1. a表直接下发，结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表直接下发，全数据拉取结果集为百万

使用hint后  
```
/*!dble:plan=a & b & c */ select  *  from t_spec_group a inner join t_spu b on a.spg_id = b.spg_id inner join t_sku c on a.spg_id=c.spg_id;
```

hint语法 a & b & c  
1. a表直接下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百
3. c表带着a表和b表的结果下发，结果集约为数百

结论：推荐使用hint写法  

使用hint后   
```
/*!dble:plan=a & ( b | c) */ select  *  from t_spec_group a inner join t_spu b on a.spg_id = b.spg_id inner join t_sku c on a.spg_id=c.spg_id;
```

hint语法 a & ( b | c)
1. a表直接下发，结果集约为数百
2. b表带着a表的结果下发，结果集约为数百，c表带着a表的结果下发，结果集约为数百

结论：当a表的结果集较小，更推荐使用该hint写法

场景三  
```
select  *  from t_spec_group a inner join t_warehouse_sku b on a.spg_id = b.spg_id left join t_sku c on b.title=c.title;
```

使用hint前（并发下发） 
1. a表直接下发，全数据拉取结果集约为数百
2. b表直接下发，全数据拉取结果集为百万
3. c表直接下发，全数据拉取结果集为百万

使用hint后   
```
/*!dble:plan=(b,c) & a */ select  *  from t_spec_group a inner join t_warehouse_sku b on a.spg_id = b.spg_id left join t_sku c on b.title=c.title;
```

hint语法 (b,c) & a  
1. b表和c表整体下发，结果集约为数百
2. a表带着b表的结果下发，结果集约为数百

结论：b表和c表存在er关系,推荐使用hint写法

使用hint后   
```
/*!dble:plan=(b,c) | a */ select  *  from t_spec_group a inner join t_warehouse_sku b on a.spg_id = b.spg_id inner join t_sku c on b.title=c.title;
```

hint语法 (b,c) | a  
1. b表和c表整体下发，结果集约为数百
2. a表带着b表的结果下发，结果集约为数百

结论：b表和c表存在er关系且a表的结果集较小，更推荐使用该hint写法

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
/*!dble:plan=1 & 2 & 3 $use_table_index*/ select * from t1 a left join t2 b on a.id = b.id left join t3 c on a.id=c.id
```
这样的话，1 就表示表 a，2 表示表 b，3 表示表 c。1，2，3表示 sql 中的 **表的别名序列号**

等价于：
```sql
/*!dble:plan=a & b & c*/ select * from t1 a left join t2 b on a.id = b.id left join t3 c on a.id=c.id
```

#### hint使用nestLoop的原则  
- hint期望的下发结果，如果违背优化的初衷那么就会报错  
举例： a join b ,如果a,b具有er关系，hint希望执行为（a & b）,那么就会报错  
- hint期望的下发方式被判定为不合理就会报错  
举例： a join b on a.col1 = b.col1 join c on c.col2 = a.col2, hint希望执行为 ( a & b & c), 那么就会报错  



## 限制

1. 对于像 Hibernate 这样自动生成表别名的框架，当前还不支持。后续会优化。
2. 当 sql 存在笛卡尔积（join 不指定关联key） 时，暂不支持，hint会报错。举例：`select * from table_a a, table_b b `
3. 当 sql 存在 多个 right join 时，暂不支持，hint会报错
4. 当 sql 存在 子查询 时，暂不支持，hint会报错
5. left join 和 inner
   join指向同一个节点的执行顺序不被允许，会报错。举例：`/*!dble:plan=a & c & b */ SELECT * FROM Employee a LEFT JOIN Dept b on a.name=b.manager inner JOIN Info c on a.name=c.name and b.manager=c.name ORDER BY a.name;`
   其中，a 和 c 可以正常 inner join ,但其结果和 b 发生join 时，需要同时完成 a 和 b 的 left join以及 c 和 b 的inner join，这在sql语法上不受支持，故不支持。
6. sql具有er关系，但是hint依旧下发成功。  
   原因：我们尽可能的按照hint期望的方式下发语句，所以dble可能尝试在内部改写sql以便满足hint的需求，举例`/*!dble:plan=a | c | b */ SELECT a.Name,a.DeptName,b.Manager,c.salary FROM Employee a LEFT JOIN Dept b on a.DeptName=b.DeptName LEFT JOIN Level c on a.Level=c.levelname and c.salary=10000 order by a.Name ;`
   会被调整为`SELECT a.Name,a.DeptName,b.Manager,c.salary FROM Employee a  LEFT JOIN Level c on a.Level=c.levelname and c.salary=10000 LEFT JOIN Dept b on a.DeptName=b.DeptName  order by a.Name`
   ,此时a表和c表不具有er关系，且er关系的检测不能跨节点，所以没有违背hint使用nestLoop的原则的第一条,可以正常下发
