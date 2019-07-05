# dble-temptable exception

## ISSUE  

- WARN [complexQueryExecutor7] (com.actiontech.dble.backend.mysql.nio.handler.query.impl.BaseSelectHandler.connectionError(BaseSelectHandler.java:147)) - com.actiontech.dble.plan.common.exception.TempTableException: temptable too much rows,[rows size is 8001] 

## Resolution  

1. 注意暂存在内存中的数据大小 
2. 调整dble中相关临时表阈值的参数 
> **注意**：参数的目的就是为了防止暂存在内存中的数据太多导致OOM。 

## Root Cause  

1. 临时表数据太多，超过了dble参数限制 
2. 具体相关参数如下： 

>**注意**：若临时表行数大于这两个值乘积，则报告错误  

| 配置名称 | 配置内容 | 默认值 |   
| ---- | ---- | ---- | ----| 
| nestLoopConnSize | 临时表阈值 | 默认4个 |  
| nestLoopRowsSize | 临时表阈值 | 默认2000行 | 

## relevant content  

**临时表**  

1. dble本身不支持临时表 
2. dble会根据具体的执行sql，将后端的查询结果放在内存中暂存 
3. nestloop中临时表阈值参数为了防止OOM
