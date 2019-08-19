# dble-Hash And ConsistentHashing And Jumpstringhash

## Questions

- dble中的hash和一致性hash是否相同
- 为什么没有一致性hash

## Conclusions

- dble中的hash并非一致性hash
- 从一致性hash的性能和均衡性来看，已被跳增一致性hash取代

## Instructions

### dble-hash

- 配置如下：

- rule.xml
```
<function name="hashLong" class="hash">
<property name="partitionCount">1,2</property>
<property name="partitionLength">10,20</property>
</function>
```
- 或者
```
<function name="hashLong" class="hash">
<property name="partitionCount">4</property>
<property name="partitionLength">10</property>
</function>
```

> **注意**：
partitionCount：指定分区的区间数  
partitionLength：指定各区间的长度  
模值（M）为最后一个区间段的末尾值（C1L1 + ... + CnLn）

对于dble-hash来说，该算法可以均匀地将数据打散到各个节点。由于该算法取余的方法强依赖于count的数目，因此，当count数发生变化的时候，datanode所对应的数据会发生剧烈变化，而发生变化的成本就是需要在count数发生变化的时候，进行数据迁移，绝大多数的数据都需要重新移动。

#### summary

因此，对于这种算法，当node数发生变化（增加、移除）后，数据项会被重新“打散”，导致大部分数据项不能落到原来的节点上，从而导致大量数据需要迁移。

### Consistent Hashing

简单来说，一致性哈希将整个哈希值空间组织成一个虚拟的圆环，整个空间按顺时针方向组织。例如我们有NodeA、Node B、Node C、Node D四个节点，有Object A、Object B、Object C、Object D四个数据对象，根据一致性哈希算法，数据A会被定为到Node A上，B被定为到Node B上，C被定为到Node C上，D被定为到Node D上:
> A ——> NodeA  
B ——> NodeB  
C ——> NodeC  
D ——> NodeD

现假设Node C不幸宕机，此时对象A、B、D不会受到影响，只有C对象被重定位到Node D。
如果在系统中增加一台服务器Node X:
> A ——> NodeA  
B ——> NodeB  
新增 ——> NodeX  
C ——> NodeC  
D ——> NodeD

对象Object A、B、D不受影响，只有对象C需要重定位到新的Node X 。
一般的，在一致性哈希算法中，如果增加一台服务器，则受影响的数据仅仅是新服务器到其环空间中前一台服务器（即沿着逆时针方向行走遇到的第一台服务器）之间数据，其它数据也不会受到影响。

虽然一致性Hash算法解决了节点变化导致的数据迁移问题，但是数据项分布的均匀性非常差，分配的很不均匀。
一致性哈希算法分布不均匀的原因是因为：数据项本身的哈希值并未发生变化，变化的是判断数据项哈希应该落到哪个节点的算法变了。主要是因为将node进行哈希后，这些值并没有均匀地落在环上，因此，这些节点所管辖的范围（每个节点实际占据环上的区间大小不）并不均匀，最终导致了数据分布的不均匀。

因此，为使得每个节点在环上所“管辖”更加均匀，一致性哈希算法引入了虚拟节点机制，即对每一个服务节点计算多个哈希，每个计算结果位置都放置一个此服务节点，称为虚拟节点。

例如：  
我们有NodeA、Node B；可以为每台服务器计算三个虚拟节点，于是可以分别计算 “Node A#1”、“Node A#2”、“Node A#3”、“Node B#1”、“Node B#2”、“Node B#3”的哈希值，形成六个虚拟节点。  
同时数据定位算法不变，只是多了一步虚拟节点到实际节点的映射，例如定位到“Node A#1”、“Node A#2”、“Node A#3”三个虚拟节点的数据均定位到Node A上,“Node B#1”、“Node B#2”、“Node B#3”三个虚拟节点的数据均定位到Node B上，用来解决分布不均的问题。
> Node A#1 ——> NodeA  
Node A#2 ——> NodeA  
Node A#3 ——> NodeA  
Node B#1 ——> NodeB  
Node B#2 ——> NodeB  
Node B#3 ——> NodeB

因此，通过增加虚节点的方法，使得每个节点在环上所“管辖”更加均匀。这样就既保证了在节点变化时，尽可能小的影响数据分布的变化，而同时又保证了数据分布的均匀。也就是靠增加“节点数量”加强管辖区间的均匀。

#### summary

| 优点 | 缺点 | 策略 |
| --- | --- | --- |
| 解决了节点变化导致的数据迁移问题 | 数据项分布的均匀性差 | 引进虚节点机制解决分布不均 |

### Jumpstringhash

- 配置如下：

- rule.xml
```
<function name="jumphash"
class="jumpStringHash">
<property name="partitionCount">2</property>
<property name="hashSlice">0:2</property>
</function>
```

> **注意**：
partitionCount：分片数量
hashSlice：分片截取长度

该算法该算法来自于Google的一篇文章A Fast, Minimal Memory, Consistent Hash Algorithm，核心思想是通过概率分布的方法将一个hash值在每个节点分布的概率变成1/n，并且可以通过更简便的方法可以计算得出，并且分布也更加均匀。  
设计目标是把对象均匀地分布在所有节点中（平衡性）；当节点数量变化时，只需要把一些对象从旧节点移动到新节点，不需要做其它移动（单调性）。

根据论文原理，可以这样说明：
- 记 ch(key,num_buckets) 为num_buckets时的hash函数。
- 当num_buckets=1时，由于只有1个bucket，显而易见，对任意k，有ch(k,1)==0。
- 当num_buckets=2时，为了使hash的结果保持均匀，ch(k,2)的结果应该有占比1/2的结果保持为0，有1/2跳变为1。
- 由此，一般规律是：num_buckets从n变化到n+1后，ch(k,n+1) 的结果中，应该有占比 n/(n+1) 的结果保持不变，而有 1/(n+1) 跳变为 n+1。

因此，我们可以用一个均匀的伪随机数生成器，来决定每次要不要跳变，论文中使用了一个64位的线性同余随机数生成器。

#### summary

> **注意**：不像割环法，跳增一致性hash不需要对key做hash，这是由于跳增一致性hash使用内置的伪随机数生成器，来对每一次key做再hash，（byron的理解：所以结果分布的均匀性与输入key的分布无关，由伪随机数生成器的均匀性保证）。
