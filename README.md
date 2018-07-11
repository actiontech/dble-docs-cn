# dble中文技术参考手册

注意,master分支上的手册适用于最新的release版,目前是2.18.06.0，其他版本的文档请参考对应分支。
            
## 目录  

* 0.概述
    * [0.1 dble 简介与整体架构](0.overview/0.1_dble_overview.md)
    * [0.2 dble对MyCat做的增强](0.overview/0.2_dble_enhance_MyCat.md)
    * [0.3 快速开始](0.overview/0.3_dble_quick_start.md)
    * [0.4 数据拆分简介](0.overview/0.4_sharding_brief_introduction.md)
* [1.配置文件](1.config_file/1.0_config_file.md)
    * [1.1 rule.xml](1.config_file/1.1_rule.xml.md)
    * [1.2 schema.xml](1.config_file/1.2_schema.xml.md)
    * [1.3 server.xml](1.config_file/1.3_server.xml.md)
    * [1.4 wrapper.conf](1.config_file/1.4_wrapper.conf.md)
    * [1.5 log4j2.xml](1.config_file/1.5_log4j2.xml.md)
    * [1.6 cache配置](1.config_file/1.6_cache.md)
    * [1.7 全局序列配置](1.config_file/1.7_global_sequence.md)
    * [1.8 myid.properties](1.config_file/1.8_myid.properties.md)
* 2.功能描述
    * [2.1 管理端命令](2.Function/2.01_manager_cmd.md)
    * [2.2 全局序列](2.Function/2.02_global_sequence.md)
    * [2.3 读写分离](2.Function/2.03_separate_RW.md)
    * [2.4 注解](2.Function/2.04_hint.md)
    * [2.5 分布式事务](2.Function/2.05_distribute_transaction.md)
    * [2.6 连接池管理](2.Function/2.06_conns_pool.md)
    * [2.7 内存管理](2.Function/2.07_memory_manager.md)
    * [2.8 集群同步协调&状态管理](2.Function/2.08_cluster.md)
    * [2.9 grpc 告警](2.Function/2.09_Grpc_warning.md)
    * [2.10 表meta数据管理](2.Function/2.10_table_meta.md)
    * [2.11 统计管理](2.Function/2.11_statistics_manager.md)
    * [2.12 故障切换](2.Function/2.12_failover.md)
    * [2.13 前后端连接检查](2.Function/2.13_conns_check.md)
    * [2.14 ER表](2.Function/2.14_ER_Split.md)
    * [2.15 global表](2.Function/2.15_global_table.md)
    * [2.16 缓存的使用](2.Function/2.16_cache.md)
    * [2.17 执行计划](2.Function/2.17_explain.md)
    * [2.18 性能观测和调整](2.Function/2.18_performance_observation.md)
* 3.语法兼容
    * [3.1 DDL](3.SQL_Syntax/3.1_DDL.md)
    * [3.2 DML](3.SQL_Syntax/3.2_DML.md)
    * [3.3 Prepared SQL Syntax](3.SQL_Syntax/3.3_Prepared_SQL_Syntax.md)
    * [3.4 Transactional and Locking Statements](3.SQL_Syntax/3.4_Transactional_and_Locking_Statements.md)
    * [3.5 DAL](3.SQL_Syntax/3.5_DAL.md)
    * [3.6 存储过程支持方式](3.SQL_Syntax/3.6_procedure_support.md)
    * [3.7 Utility Statements](3.SQL_Syntax/3.7_Utility_Statements.md)
    * [3.8 Hint](3.SQL_Syntax/3.8_Hint.md)
    * [3.9 其他不支持语句](3.SQL_Syntax/3.9_Other_unsupport.md)
* 4.协议兼容
    * [4.0 基本包](4.Protocol/4.0_Packet.md)
    * [4.1 连接建立](4.Protocol/4.1_Connecting.md)
    * [4.2 文本协议](4.Protocol/4.2_Text_Protocol.md)
    * [4.3 二进制协议 (Prepared Statements)](4.Protocol/4.3_Binary_Protocol.md)
    * [4.4 服务响应包](4.Protocol/4.4_Server_Response_Packets.md)
* 5.已知限制
    * [5.1 druid引发的限制](5.Limit/5.1_druid_limit.md)
    * [5.2 其他已知限制](5.Limit/5.2_other_limit.md)
* [6.与MySQL Server的差异化描述](6.Differernce_from_MySQL_Server/6.Differernce_from_MySQL_Server.md)
* 7.开发者须知
    * [7.1 SQL开发编写原则](7.Developer_Notice/7.1_SQL_develop_rule.md)
    * [7.2 dble连接Demo](7.Developer_Notice/7.2_Demo_for_connect_dble.md)
    * [7.3 其他注意事项](7.Developer_Notice/7.3_Other_Notice.md)

