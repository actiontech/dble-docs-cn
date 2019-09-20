# dble-PasswordFormat

Ushard 06 版本新增了配置文件中的密码自动加密功能，表现为：在 umc 上完成配置后，服务器上的 server.xml、schema.xml 中的 password 字段会自动加密。

## Method

- **第一种方式：**  
在umc上的xml配置文件中，password字段使用明文，参数配置usingDecrypt=“0”（或者不配，默认为0）  

使用这样的方式，在umc上密码为明文，服务器上的配置文件中密码为密文  

| 配置名称 | 配置内容 | 多节点 | 可选项/默认值 | 详细描述 |  
| ---- | ---- | ---- | ---- | ---- |  
| usingDecrypt | 是否启用加密password | 否 | 候选值0/1，默认值0 | 如果设置为1，password属性值应该为使用脚本encrypt.sh加密串得到的密文

### For Example 
umc上配置，在server.xml中,参数配置，密码明文  
```xml
<user name="root">
        <property name="usingDecrypt">0</property>
        <property name="password">1</property>
        <property name="schemas">testdb,a</property>
    </user>
```
umc上配置，在schema.xml中
```xml
<dataHost name="dh1" maxCon="1000" minCon="5" balance="3" switchType="-1" slaveThreshold="100">
        <heartbeat>show slave status</heartbeat>
        <writeHost host="hostS2" url="172.23.134.2:3306" user="root" password="123456" id="mysql-0f9afs">
            <readHost host="hostM1" url="172.23.134.3:3306" user="root" password="123456" id="mysql-mj68pp"></readHost>
        </writeHost>
    </dataHost>
```
查看后端server.xml配置文件，密码密文
```xml
<user name="root">
        <property name="schemas">testdb,a</property>
        <property name="password">GK56FpVYZefmx+GUcy9Fm18MGeXRIfwpGrNYCn9GhaqeSbDonfNmKQcL/ex+B3XJ1D/Kfut5FehOQGRymx/9Wg==</property>
        <property name="usingDecrypt">1</property>
    </user>
```
查看后端schema.xml配置文件，密码密文
```xml
<dataHost name="dh1" maxCon="1000" minCon="5" balance="3" switchType="-1" slaveThreshold="100">
        <heartbeat>show slave status</heartbeat>
        <writeHost host="hostS2" url="172.23.134.2:3306" user="root" password="RmQjaTuWFq5/y+Hp0SK7yIQ7Be76iohMnVrQEiAlxSwNm8HeM5a0B3Fd5g129ilKlTjts7OrDI8s/p+US/Amtg==" id="mysql-0f9afs" usingDecrypt="1">
            <readHost host="hostM1" url="172.23.134.3:3306" user="root" password="BpkNIjF7LfzS1C76HT7B1bJgmGIDtPihqIvHBlC92L1IFqsMfoJEMk1EkxSzjasWB4GWoUcODYO4AaJstdAp5w==" id="mysql-mj68pp" usingDecrypt="1"></readHost>
        </writeHost>
    </dataHost>
```

- **第二种方式：**  
在umc上的xml配置文件中，password字段使用密文，参数配置usingDecrypt=“1”  

使用这样的方式，在umc上和服务器上的配置文件中密码均为密文

### For Example 

- 前端密码加密  
通过执行脚本：bash encrypt.sh 0:{user}:{password}的结果进行获取密文  
bash encrypt.sh 0:{user}:{password}中的0代表前端加密，标志位  
例如：  
```
bash encrypt.sh 0:root:1  
/data/ushard/jre/bin/java -cp /data/ushard/core/lib/dble*.jar com.actiontech.dble.util.DecryptUtil password=******
GO0bnFVWrAuFgr1JMuMZkvfDNyTpoiGU7n/Wlsa151CirHQnANVk3NzE3FErx8v6pAcO0ctX3xFecmSr+976QA==
```
在umc上配置server.xml  
```xml
<user name="root">
        <property name="usingDecrypt">1</property>
        <property name="password">GK56FpVYZefmx+GUcy9Fm18MGeXRIfwpGrNYCn9GhaqeSbDonfNmKQcL/ex+B3XJ1D/Kfut5FehOQGRymx/9Wg==</property>
        <property name="schemas">testdb,a</property>
    </user>
```
- 后端密码加密  
通过执行脚本：bash encrypt.sh 1:host:user:password 的结果进行获取密文  
bash encrypt.sh 1:host:user:password中的1代表后端加密，标志位  
例如：  
```
bash encrypt.sh 1:hostS2:root:123456
/data/ushard/jre/bin/java -cp /data/ushard/core/lib/dble*.jar com.actiontech.dble.util.DecryptUtil password=******
AnoD8gmmRAvV650eNmZMVx63528rZmV+swnG42TwXSaXpZCOvTdU4kRBb5lam7TQnOWDzakZO1dAwrwAEUEPKQ==
``` 
```
encrypt.sh 1:hostM1:root:123456
/data/ushard/jre/bin/java -cp /data/ushard/core/lib/dble*.jar com.actiontech.dble.util.DecryptUtil password=******
BpkNIjF7LfzS1C76HT7B1bJgmGIDtPihqIvHBlC92L1IFqsMfoJEMk1EkxSzjasWB4GWoUcODYO4AaJstdAp5w==
``` 
在umc上配置schema.xml
```xml
<dataHost name="dh1" maxCon="1000" minCon="5" balance="3" switchType="-1" slaveThreshold="100">
       <heartbeat>show slave status</heartbeat>
       <writeHost host="hostS2" url="172.23.134.2:3306" user="root" password="RmQjaTuWFq5/y+Hp0SK7yIQ7Be76iohMnVrQEiAlxSwNm8HeM5a0B3Fd5g129ilKlTjts7OrDI8s/p+US/Amtg==" id="mysql-0f9afs" usingDecrypt="1">
           <readHost host="hostM1" url="172.23.134.3:3306" user="root" password="BpkNIjF7LfzS1C76HT7B1bJgmGIDtPihqIvHBlC92L1IFqsMfoJEMk1EkxSzjasWB4GWoUcODYO4AaJstdAp5w==" id="mysql-mj68pp" usingDecrypt="1"></readHost>
       </writeHost>
   </dataHost>
```
查看后端server.xml配置文件，密码密文  
```xml
<user name="root">
       <property name="schemas">testdb,a</property>
       <property name="password">GK56FpVYZefmx+GUcy9Fm18MGeXRIfwpGrNYCn9GhaqeSbDonfNmKQcL/ex+B3XJ1D/Kfut5FehOQGRymx/9Wg==</property>
       <property name="usingDecrypt">1</property>
   </user>
```
查看后端schema.xml配置文件，密码密文  
```xml
 <dataHost name="dh1" maxCon="1000" minCon="5" balance="3" switchType="-1" slaveThreshold="100">
<heartbeat>show slave status</heartbeat>
        <writeHost host="hostS2" url="172.23.134.2:3306" user="root" password="RmQjaTuWFq5/y+Hp0SK7yIQ7Be76iohMnVrQEiAlxSwNm8HeM5a0B3Fd5g129ilKlTjts7OrDI8s/p+US/Amtg==" id="mysql-0f9afs" usingDecrypt="1">
            <readHost host="hostM1" url="172.23.134.3:3306" user="root" password="BpkNIjF7LfzS1C76HT7B1bJgmGIDtPihqIvHBlC92L1IFqsMfoJEMk1EkxSzjasWB4GWoUcODYO4AaJstdAp5w==" id="mysql-mj68pp" usingDecrypt="1"></readHost>
        </writeHost>
    </dataHost>
```
