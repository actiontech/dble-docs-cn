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

对于dble-hash来说，该算法可以均匀地将数据分布到各个节点。  

例如：  
Count=2,Length=2 -> [0,2)[2,4) -> 模值=4  
key分别取值：1,2,3,4,5,6,7,8  
数据分布情况如下：    

| node1 | node2 |  
| ----- | ----- |  
| 1,4,5,8 | 2,3,6,7 |

当增加一个节点，使Count=3,Length=2 -> [0,2)[2,4)[4,6) -> 模值=6
key分别取值：1,2,3,4,5,6,7,8  
数据分布情况如下： 
   
| node1 | node2 | node3 |  
| ----- | ----- | ----- | 
| 1,6,7 | 2,3,8 | 4,5 |

从这个例子中我们可以看出：当节点数增加时，大多数旧的数据都需要重新分布，而重新分布的成本就是需要在count数发生变化的时候，进行数据迁移，大多数的数据都需要重新移动。

#### summary

因此，对于这种算法，当node数发生变化（增加、移除）后，大多数旧的数据都需要重新分布，并进行数据迁移。

### Consistent Hashing

一致性哈希，将整个哈希值空间组织成一个虚拟的圆环，整个空间按顺时针方向组织。例如我们有NodeA、Node B、Node C、Node D四个节点，有数据A、数据B、数据C、数据D四个数据对象，根据一致性哈希算法，数据A会被分布到Node A上，B被分布到Node B上，C被分布到Node C上，D被分布到Node D上:
> 数据A ——> NodeA  
数据B ——> NodeB  
数据C ——> NodeC  
数据D ——> NodeD

现假设Node C不幸宕机，此时数据A、B、D不会受到影响，只有数据C被重分布到Node D。
如果在系统中增加一台服务器Node X:
> 数据A ——> NodeA  
数据B ——> NodeB  
新增 ——> NodeX  
数据C ——> NodeC  
数据D ——> NodeD

数据A、B、D不受影响，只有数据C需要重分布到新的Node X 。也就是说旧数据之间不会发生数据的变动。

虽然一致性Hash算法解决了节点变化导致的数据迁移问题，但是数据项分布的均匀性不够好。
一致性哈希算法分布不均匀的原因是因为：将node进行哈希后，这些值并没有均匀地落在环上，因此，这些节点所管辖的范围（每个节点实际占据环上的区间大小不）并不均匀，最终导致了数据分布的不均匀。

因此，为使得每个节点在环上所“管辖”更加均匀，一致性哈希算法引入了虚拟节点机制，即对每一个服务节点计算多个哈希，每个计算结果位置都放置一个服务节点，称为虚拟节点。

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

对于一致性hash算法来说，当node数发生变化（增加、移除）后，旧的数据之间没有发生变动，只是需要将部分旧数据重新分布到新的节点。

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
- 假设我们有四个节点，比作为四个桶，分别是：0,1,2,3。
- 数据落入第一个桶(0)的概率是1；落入第二个桶(1)的概率是1/(n+1)，也就是1/2；落入第三个桶(2)的概率是1/(n+1)，也就是1/3；落入第四个桶(3)的概率是1/(n+1)，也就是1/4。
- 由此，一般规律是：数据落入每个桶中的概率，有占比 n/(n+1) 的结果保持不变，而有 1/(n+1) 跳变为 n+1。
- 而每个数据都是落入至index（max）中，比如数据a有概率分布至0,1,2,3四个桶中，那么最终会落入至3内。

当增加一个桶时：由0,1,2,3变为0,1,2,3,4，需要重新分布的数据仅是每个桶内有概率分布到新桶内的数据，旧数据之间不会发生变化。  

#### summary

由于Jumpstringhash采用的是概率分布的结果，因此计算相对于一致性hash较简单。

> 
- Jumpstringhash相比于一致性hash算法来说，占用内存更小，计算更快，数据分布更均匀；
- Jumpstringhash和一致性hash算法，对于节点变动的情况下，都是将部分旧数据重新分布到新节点上，旧数据之间不会发生变动；
- Jumpstringhash和一致性hash算法相比于dble-hash来说，节点变动的情况下，旧数据之间不会发生变动；
- dble-hash相比于Jumpstringhash和一致性hash算法来说，最大的优势在于计算方便，可以人为的快速计算出结果。



