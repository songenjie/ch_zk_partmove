##  PartMove
Hot and cold separation of metadata in zookeeper by Clickhouse

## Clickhouse
[link](https://github.com/songenjie/ClickHouse/tree/20.12_ColdZooKeeper)


## 1 问题
* Zookeeper 元数据量大，snaptshot 较慢
* Zookeeper znode 量较大

## 2 解决方案
因olap较多为增量数据，历史的数据不变更，将元数据按冷热隔离
```
持久化的数据= 元数据+事务日志

元数据=冷元数据+热的元数据

事务本身是不能分离

现在zookeeper 的瓶颈

1. 高并发写的吞吐 ，因为zookeeper 是强一致性的服务 ，写只能交给master
2. 高并发写的瓶颈

1. cpu mem 占用较高 （ 元数据量大 占用较高  2 事务量大）


1 元数据量大，进程处理就会很慢
2 元数据量大， snaptshot 即使是异步的，但是，占用资源且耗时较长


解决方法
1 元数据从每个表的parition 粒度做隔离，做冷热


1 冷的数据基本不发生变化，今儿如果它存在一个单独的zookeeper 集群就
    虽然它的数据量，但是对他的操作基本都是读 ，读是可以通过扩容节点解决的
     数据发生变化，那么数据就很少发生snaptshot ,snaptshot 使用的资源也就少，今儿也就降级了集群的压力
2 热数据不变化
```

## 3 过程
1. java 程序分析parition 变更状态，将冷的part 迁移到另外一个zookeeper 集群
2. java 程序在源zk集群添加 shard/block_number/{partition}/State 字段 内容为 HOT/COLD
3. clickhouse  配置中添加 cold_zookeeper 配置
4. clickhouse  处理 clickhouse 进程重启 checkpart 操作 和 drop parition 操作

## 4 测试
1 java part move 程序
```
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Replica - start init /clickhouse/***/jdob_ha/jason/table_test_local/02/replicas/02 part done
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - start init /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02  replicas done
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - part /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/00/parts/20191214_0_0_0 date time is 2021-01-11 20:11:00
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - update parition time to 2021-01-11 20:11:00
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - part /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/00/parts/20191215_0_0_0 date time is 2021-01-11 20:11:05
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - update parition time to 2021-01-11 20:11:05
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - part /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/00/parts/20191213_0_0_0 date time is 2021-01-11 20:10:56
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - update parition time to 2021-01-11 20:10:56
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - part /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/01/parts/20191213_0_0_0 date time is 2021-01-11 20:10:55
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - part /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/01/parts/20191214_0_0_0 date time is 2021-01-11 20:11:00
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - part /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/01/parts/20191215_0_0_0 date time is 2021-01-11 20:11:05
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - part /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/02/parts/20191215_0_0_0 date time is 2021-01-11 20:11:05
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - part /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/02/parts/20191213_0_0_0 date time is 2021-01-11 20:10:56
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - part /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/02/parts/20191214_0_0_0 date time is 2021-01-11 20:11:00
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - partition : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191213 time: + 2021-01-11 20:10:56 state: HOT
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - partition : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191215 time: + 2021-01-11 20:11:05 state: COLD
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - partition : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191214 time: + 2021-01-11 20:11:00 state: COLD
2021-01-12 09:38:01,278 [Timer-0] INFO  metadata.Shard - start set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191213/State to HOT
2021-01-12 09:38:01,288 [Timer-0] INFO  metadata.Shard - set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191213/State to HOT done
2021-01-12 09:38:01,288 [Timer-0] INFO  metadata.Shard - start set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191215/State to HOT
2021-01-12 09:38:01,296 [Timer-0] INFO  metadata.Shard - set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191215/State to HOT done
2021-01-12 09:38:01,296 [Timer-0] INFO  metadata.Shard - start set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191214/State to HOT
2021-01-12 09:38:01,304 [Timer-0] INFO  metadata.Shard - set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191214/State to HOT done
2021-01-12 09:38:01,304 [Timer-0] INFO  metadata.Shard - start set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191215/State to COLD
2021-01-12 09:38:01,313 [Timer-0] INFO  metadata.Shard - start set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191215/State to COLD done
2021-01-12 09:38:01,313 [Timer-0] INFO  metadata.Shard - start set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191214/State to COLD
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - start set /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191214/State to COLD done
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - cold partition :/clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191215
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - cold partition :/clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/block_numbers/20191214
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - partnumber : 20191214
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - parts : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/00/parts/20191214_0_0_0 need move
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - partnumber : 20191215
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - parts : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/00/parts/20191215_0_0_0 need move
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - partnumber : 20191213
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - parts : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/00/parts/20191213_0_0_0 need drop
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - partnumber : 20191213
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - parts : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/01/parts/20191213_0_0_0 need drop
2021-01-12 09:38:01,321 [Timer-0] INFO  metadata.Shard - partnumber : 20191214
2021-01-12 09:38:01,322 [Timer-0] INFO  metadata.Shard - parts : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/01/parts/20191214_0_0_0 need move
2021-01-12 09:38:01,322 [Timer-0] INFO  metadata.Shard - partnumber : 20191215
2021-01-12 09:38:01,322 [Timer-0] INFO  metadata.Shard - parts : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/01/parts/20191215_0_0_0 need move
2021-01-12 09:38:01,322 [Timer-0] INFO  metadata.Shard - partnumber : 20191215
2021-01-12 09:38:01,322 [Timer-0] INFO  metadata.Shard - parts : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/02/parts/20191215_0_0_0 need move
2021-01-12 09:38:01,322 [Timer-0] INFO  metadata.Shard - partnumber : 20191213
2021-01-12 09:38:01,322 [Timer-0] INFO  metadata.Shard - parts : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/02/parts/20191213_0_0_0 need drop
2021-01-12 09:38:01,322 [Timer-0] INFO  metadata.Shard - partnumber : 20191214
2021-01-12 09:38:01,322 [Timer-0] INFO  metadata.Shard - parts : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/02/parts/20191214_0_0_0 need move
2021-01-12 09:38:01,322 [Timer-0] INFO  jdbc.ClickHouse - Init Shards done ..
2021-01-12 09:38:01,322 [Timer-0] INFO  PartMove - source of shard path  : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/00/replicas
2021-01-12 09:38:01,323 [Timer-0] INFO  zkreader.Reader - Reading /clickhouse/ClusterName/jdob_ha/jason/table_test_local/00/replicas from ip-.58:port
2021-01-12 09:38:02,324 [Timer-0] INFO  zkreader.Reader - Processing, total=29, processed=29
2021-01-12 09:38:02,324 [Timer-0] INFO  zkreader.Reader - Completed.
2021-01-12 09:38:02,405 [Timer-0] INFO  PartMove - start copy znode :/clickhouse/ClusterName/jdob_ha/jason/table_test_local/00/replicas
2021-01-12 09:38:02,407 [Timer-0] INFO  PartMove - target of shard path ip-.6:port/clickhouse/ClusterName/jdob_ha/jason/table_test_local/00/replicas
2021-01-12 09:38:02,408 [Timer-0] INFO  zkwriter.Writer - Writing data...
2021-01-12 09:38:02,463 [Timer-0] INFO  zkwriter.Writer - Writing data completed.
2021-01-12 09:38:02,463 [Timer-0] INFO  zkwriter.Writer - Wrote 27 nodes
2021-01-12 09:38:02,463 [Timer-0] INFO  zkwriter.Writer - Created 0 nodes; Updated 27 nodes
2021-01-12 09:38:02,463 [Timer-0] INFO  zkwriter.Writer - Ignored 2 ephemeral nodes
2021-01-12 09:38:02,463 [Timer-0] INFO  zkwriter.Writer - Skipped 0 nodes older than -1
2021-01-12 09:38:02,463 [Timer-0] INFO  zkwriter.Writer - Max mtime of copied nodes: 1610367341877
2021-01-12 09:38:02,463 [Timer-0] INFO  PartMove - hot parts size 0
2021-01-12 09:38:02,463 [Timer-0] INFO  PartMove - source of shard path  : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/01/replicas
2021-01-12 09:38:02,463 [Timer-0] INFO  zkreader.Reader - Reading /clickhouse/ClusterName/jdob_ha/jason/table_test_local/01/replicas from ip-.58:port
2021-01-12 09:38:03,464 [Timer-0] INFO  zkreader.Reader - Processing, total=29, processed=29
2021-01-12 09:38:03,464 [Timer-0] INFO  zkreader.Reader - Completed.
2021-01-12 09:38:03,572 [Timer-0] INFO  PartMove - start copy znode :/clickhouse/ClusterName/jdob_ha/jason/table_test_local/01/replicas
2021-01-12 09:38:03,573 [Timer-0] INFO  PartMove - target of shard path ip-.6:port/clickhouse/ClusterName/jdob_ha/jason/table_test_local/01/replicas
2021-01-12 09:38:03,573 [Timer-0] INFO  zkwriter.Writer - Writing data...
2021-01-12 09:38:03,624 [Timer-0] INFO  zkwriter.Writer - Writing data completed.
2021-01-12 09:38:03,625 [Timer-0] INFO  zkwriter.Writer - Wrote 27 nodes
2021-01-12 09:38:03,625 [Timer-0] INFO  zkwriter.Writer - Created 0 nodes; Updated 27 nodes
2021-01-12 09:38:03,625 [Timer-0] INFO  zkwriter.Writer - Ignored 2 ephemeral nodes
2021-01-12 09:38:03,625 [Timer-0] INFO  zkwriter.Writer - Skipped 0 nodes older than -1
2021-01-12 09:38:03,625 [Timer-0] INFO  zkwriter.Writer - Max mtime of copied nodes: 1610366881035
2021-01-12 09:38:03,625 [Timer-0] INFO  PartMove - hot parts size 0
2021-01-12 09:38:03,625 [Timer-0] INFO  PartMove - source of shard path  : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas
2021-01-12 09:38:03,625 [Timer-0] INFO  zkreader.Reader - Reading /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas from ip-.58:port
2021-01-12 09:38:04,625 [Timer-0] INFO  zkreader.Reader - Processing, total=52, processed=52
2021-01-12 09:38:04,626 [Timer-0] INFO  zkreader.Reader - Completed.
2021-01-12 09:38:04,705 [Timer-0] INFO  PartMove - start copy znode :/clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas
2021-01-12 09:38:04,706 [Timer-0] INFO  PartMove - target of shard path ip-.6:port/clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas
2021-01-12 09:38:04,706 [Timer-0] INFO  zkwriter.Writer - Writing data...
2021-01-12 09:38:04,794 [Timer-0] INFO  zkwriter.Writer - Writing data completed.
2021-01-12 09:38:04,794 [Timer-0] INFO  zkwriter.Writer - Wrote 49 nodes
2021-01-12 09:38:04,794 [Timer-0] INFO  zkwriter.Writer - Created 0 nodes; Updated 49 nodes
2021-01-12 09:38:04,794 [Timer-0] INFO  zkwriter.Writer - Ignored 3 ephemeral nodes
2021-01-12 09:38:04,794 [Timer-0] INFO  zkwriter.Writer - Skipped 0 nodes older than -1
2021-01-12 09:38:04,794 [Timer-0] INFO  zkwriter.Writer - Max mtime of copied nodes: 1610367548873
2021-01-12 09:38:04,794 [Timer-0] INFO  PartMove - hot parts size 3
2021-01-12 09:38:04,794 [Timer-0] INFO  PartMove - drop hot part from targe path : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/00/parts/20191213_0_0_0
2021-01-12 09:38:04,798 [Timer-0] INFO  PartMove - drop hot part from targe path : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/01/parts/20191213_0_0_0
2021-01-12 09:38:04,802 [Timer-0] INFO  PartMove - drop hot part from targe path : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/02/parts/20191213_0_0_0
2021-01-12 09:38:04,806 [Timer-0] INFO  PartMove - drop cold  part from source path : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/00/parts/20191214_0_0_0
2021-01-12 09:38:04,812 [Timer-0] INFO  PartMove - drop cold  part from source path : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/00/parts/20191215_0_0_0
2021-01-12 09:38:04,821 [Timer-0] INFO  PartMove - drop cold  part from source path : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/01/parts/20191214_0_0_0
2021-01-12 09:38:04,829 [Timer-0] INFO  PartMove - drop cold  part from source path : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/01/parts/20191215_0_0_0
2021-01-12 09:38:04,837 [Timer-0] INFO  PartMove - drop cold  part from source path : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/02/parts/20191215_0_0_0
2021-01-12 09:38:04,846 [Timer-0] INFO  PartMove - drop cold  part from source path : /clickhouse/ClusterName/jdob_ha/jason/table_test_local/02/replicas/02/parts/20191214_0_0_0
```

2. 主 zookeeper 状态
```
20191213 HOT
20191214 COLD
20191215 COLD
![image](/uploads/bee71b4d3774fc09700444cd3655393d/image.png)
```

3. 备用 zookeeper
```
COLD par 存储备用zookeeper 集群,并删除源集群 part
HOT  par 不变更
![image](/uploads/742d0014bd934a2fb9eb26c98fab9847/image.png)
```

4. clickhouse 配置
- config.xml
```xml
    <zookeeper incl="zookeeper-servers" optional="true" />
    <cold_zookeeper incl="cold-zookeeper-servers" optional="true" />
```
- metrika.xml
```xml
<zookeeper-servers>
  <node index="1">
    <host>ip-.58</host>
    <port>port</port>
  </node>
  <node index="2">
    <host>ip-.91</host>
    <port>port</port>
  </node>
  <node index="3">
    <host>ip-.69</host>
    <port>port</port>
  </node>
</zookeeper-servers>

<cold-zookeeper-servers>
  <node index="1">
    <host>ip-.6</host>
    <port>port</port>
  </node>
  <node index="2">
    <host>ip-.7</host>
    <port>port</port>
  </node>
  <node index="3">
    <host>ip-.8</host>
    <port>port</port>
  </node>
</cold-zookeeper-servers>
```

5. 重启验证成功
6. drop parition 验证
- clickhouse drop parition sql
```sql
ip :) alter table  jason.table_test_local drop partition 20191213;

ALTER TABLE jason.table_test_local
    DROP PARTITION 20191213


[ip] 2021.01.12 10:18:58.930225 [ 147090 ] {9552c576-5ef4-4181-901f-acfc15de094b} <Debug> executeQuery: (from [::ffff:172.18.160.19]:10942) alter table jason.table_test_local drop partition 20191213;
[ip] 2021.01.12 10:18:58.930338 [ 147090 ] {9552c576-5ef4-4181-901f-acfc15de094b} <Trace> ContextAccess (default): Access granted: ALTER DELETE ON jason.table_test_local
[ip] 2021.01.12 10:18:58.963670 [ 147090 ] {9552c576-5ef4-4181-901f-acfc15de094b} <Trace> jason.table_test_local: Deleted 0 deduplication block IDs in partition ID 20191213
[ip] 2021.01.12 10:18:58.963691 [ 147090 ] {9552c576-5ef4-4181-901f-acfc15de094b} <Debug> jason.table_test_local: Disabled merges covered by range 20191213_0_2_999999999
[ip] 2021.01.12 10:18:58.971998 [ 147090 ] {9552c576-5ef4-4181-901f-acfc15de094b} <Debug> jason.table_test_local: Waiting for 00 to pull log-0000000005 to queue
[ip] 2021.01.12 10:18:59.024518 [ 147090 ] {9552c576-5ef4-4181-901f-acfc15de094b} <Debug> jason.table_test_local: Looking for node corresponding to log-0000000005 in 00 queue
[ip] 2021.01.12 10:18:59.039725 [ 147090 ] {9552c576-5ef4-4181-901f-acfc15de094b} <Debug> jason.table_test_local: No corresponding node found. Assuming it has been already processed. Found 0 nodes
[ip] 2021.01.12 10:18:59.046882 [ 147090 ] {9552c576-5ef4-4181-901f-acfc15de094b} <Debug> MemoryTracker: Peak memory usage (for query): 0.00 B.
Ok.

0 rows in set. Elapsed: 0.121 sec.

ip :) alter table  jason.table_test_local drop partition 20191215;

ALTER TABLE jason.table_test_local
    DROP PARTITION 20191215


[ip] 2021.01.12 10:19:02.208074 [ 147090 ] {463ea1cf-5b03-4451-926c-998507eff901} <Debug> executeQuery: (from [::ffff:172.18.160.19]:10942) alter table jason.table_test_local drop partition 20191215;
[ip] 2021.01.12 10:19:02.208189 [ 147090 ] {463ea1cf-5b03-4451-926c-998507eff901} <Trace> ContextAccess (default): Access granted: ALTER DELETE ON jason.table_test_local
[ip] 2021.01.12 10:19:02.238566 [ 147090 ] {463ea1cf-5b03-4451-926c-998507eff901} <Trace> jason.table_test_local: Deleted 0 deduplication block IDs in partition ID 20191215
[ip] 2021.01.12 10:19:02.238589 [ 147090 ] {463ea1cf-5b03-4451-926c-998507eff901} <Debug> jason.table_test_local: Disabled merges covered by range 20191215_0_2_999999999
[ip] 2021.01.12 10:19:02.246703 [ 147090 ] {463ea1cf-5b03-4451-926c-998507eff901} <Debug> jason.table_test_local: Waiting for 00 to pull log-0000000006 to queue
[ip] 2021.01.12 10:19:02.263364 [ 147090 ] {463ea1cf-5b03-4451-926c-998507eff901} <Debug> jason.table_test_local: Looking for node corresponding to log-0000000006 in 00 queue
[ip] 2021.01.12 10:19:02.271617 [ 147090 ] {463ea1cf-5b03-4451-926c-998507eff901} <Debug> jason.table_test_local: No corresponding node found. Assuming it has been already processed. Found 0 nodes
[ip] 2021.01.12 10:19:02.271952 [ 147090 ] {463ea1cf-5b03-4451-926c-998507eff901} <Debug> MemoryTracker: Peak memory usage (for query): 0.00 B.
Ok.

0 rows in set. Elapsed: 0.068 sec.

```

- clickhouse log
```
2021.01.12 10:09:15.358487 [ 24059 ] {fa31ebd4-0b25-4d92-9b78-099a357dc363} <Debug> executeQuery: (from [::ffff:172.18.160.19]:52644) alter table jason.table_test_local drop partition 20191215;
2021.01.12 10:09:15.358682 [ 24059 ] {fa31ebd4-0b25-4d92-9b78-099a357dc363} <Trace> ContextAccess (default): Access granted: ALTER DELETE ON jason.table_test_local
2021.01.12 10:09:15.430511 [ 24059 ] {fa31ebd4-0b25-4d92-9b78-099a357dc363} <Trace> jason.table_test_local: Deleted 1 deduplication block IDs in partition ID 20191215
2021.01.12 10:09:15.430577 [ 24059 ] {fa31ebd4-0b25-4d92-9b78-099a357dc363} <Debug> jason.table_test_local: Disabled merges covered by range 20191215_0_1_999999999
2021.01.12 10:09:15.439021 [ 24059 ] {fa31ebd4-0b25-4d92-9b78-099a357dc363} <Debug> jason.table_test_local: Waiting for 01 to pull log-0000000003 to queue
2021.01.12 10:09:15.440626 [ 24036 ] {} <Debug> jason.table_test_local (ReplicatedMergeTreeQueue): Pulling 1 entries to queue: log-0000000003 - log-0000000003
2021.01.12 10:09:15.463915 [ 24036 ] {} <Debug> jason.table_test_local (ReplicatedMergeTreeQueue): Pulled 1 entries to queue.
2021.01.12 10:09:15.464253 [ 24059 ] {fa31ebd4-0b25-4d92-9b78-099a357dc363} <Debug> jason.table_test_local: Looking for node corresponding to log-0000000003 in 01 queue
2021.01.12 10:09:15.464510 [ 24079 ] {} <Debug> jason.table_test_local (ReplicatedMergeTreeQueue): Removed 0 entries from queue. Waiting for 0 entries that are currently executing.
2021.01.12 10:09:15.464545 [ 24079 ] {} <Debug> jason.table_test_local: Removing parts.
2021.01.12 10:09:15.464668 [ 24059 ] {fa31ebd4-0b25-4d92-9b78-099a357dc363} <Debug> jason.table_test_local: Waiting for queue-0000000003 to disappear from 01 queue
2021.01.12 10:09:15.465803 [ 24079 ] {} <Debug> jason.table_test_local: Removed 1 parts inside 20191215_0_1_999999999.
2021.01.12 10:09:15.465907 [ 24044 ] {} <Trace> jason.table_test_local: Found 1 old parts to remove.
2021.01.12 10:09:15.465927 [ 24044 ] {} <Debug> jason.table_test_local: Removing 1 old parts from ZooKeeper
2021.01.12 10:09:15.472331 [ 24044 ] {} <Debug> jason.table_test_local: There is no part 20191215_0_0_0 in ZooKeeper, it was only in filesystem
2021.01.12 10:09:15.472368 [ 24044 ] {} <Debug> jason.table_test_local: Removed 1 old parts from ZooKeeper. Removing them from filesystem.
2021.01.12 10:09:15.472787 [ 24044 ] {} <Debug> jason.table_test_local: Removed 1 old parts
2021.01.12 10:09:15.472793 [ 24059 ] {fa31ebd4-0b25-4d92-9b78-099a357dc363} <Debug> MemoryTracker: Peak memory usage (for query): 0.00 B.
2021.01.12 10:09:15.473007 [ 24059 ] {} <Debug> TCPHandler: Processed in 0.114860664 sec.
```

7. 单个shard 内其他副本同步日志
```
2021.01.12 10:22:30.890412 [ 24036 ] {} <Debug> DNSResolver: Updating DNS cache
2021.01.12 10:22:30.890447 [ 24036 ] {} <Debug> DNSResolver: Updated DNS cache
2021.01.12 10:22:42.832496 [ 24041 ] {} <Debug> jason.table_test_local (ReplicatedMergeTreeQueue): Pulling 1 entries to queue: log-0000000008 - log-0000000008
2021.01.12 10:22:42.857994 [ 24041 ] {} <Debug> jason.table_test_local (ReplicatedMergeTreeQueue): Pulled 1 entries to queue.
2021.01.12 10:22:42.858786 [ 24078 ] {} <Debug> jason.table_test_local (ReplicatedMergeTreeQueue): Removed 0 entries from queue. Waiting for 0 entries that are currently executing.
2021.01.12 10:22:42.858819 [ 24078 ] {} <Debug> jason.table_test_local: Removing parts.
2021.01.12 10:22:42.860059 [ 24078 ] {} <Debug> jason.table_test_local: Removed 1 parts inside 20191214_0_1_999999999.
2021.01.12 10:22:42.860147 [ 24038 ] {} <Trace> jason.table_test_local: Found 1 old parts to remove.
2021.01.12 10:22:42.860181 [ 24038 ] {} <Debug> jason.table_test_local: Removing 1 old parts from ZooKeeper
2021.01.12 10:22:42.866228 [ 24038 ] {} <Debug> jason.table_test_local: There is no part 20191214_0_0_0 in ZooKeeper, it was only in filesystem
2021.01.12 10:22:42.866258 [ 24038 ] {} <Debug> jason.table_test_local: Removed 1 old parts from ZooKeeper. Removing them from filesystem.
2021.01.12 10:22:42.866563 [ 24038 ] {} <Debug> jason.table_test_local: Removed 1 old parts
2021.01.12 10:22:45.714737 [ 24042 ] {} <Trace> SystemLog (system.part_log): Flushing system log, 1 entries to flush
2021.01.12 10:22:45.715050 [ 24042 ] {} <Debug> DiskLocal: Reserving 1.00 MiB on disk `default`, having unreserved 1.68 TiB.
2021.01.12 10:22:45.715713 [ 24042 ] {} <Trace> system.part_log: Renaming temporary part tmp_insert_20210111_3_3_0 to 20210111_6_6_0.
2021.01.12 10:22:45.715812 [ 24042 ] {} <Trace> SystemLog (system.part_log): Flushed system log
2021.01.12 10:22:45.890611 [ 24046 ] {} <Debug> DNSResolver: Updating DNS cache
2021.01.12 10:22:45.890666 [ 24046 ] {} <Debug> DNSResolver: Updated DNS cache
2021.01.12 10:23:00.890834 [ 24031 ] {} <Debug> DNSResolver: Updating DNS cache
2021.01.12 10:23:00.890905 [ 24031 ] {} <Debug> DNSResolver: Updated DNS cache
2021.01.12 10:23:15.891062 [ 24040 ] {} <Debug> DNSResolver: Updating DNS cache
2021.01.12 10:23:15.891103 [ 24040 ] {} <Debug> DNSResolver: Updated DNS cache
2021.01.12 10:23:17.114863 [ 24035 ] {} <Debug> jason.table_test_local (ReplicatedMergeTreeQueue): Pulling 1 entries to queue: log-0000000009 - log-0000000009
2021.01.12 10:23:17.130722 [ 24035 ] {} <Debug> jason.table_test_local (ReplicatedMergeTreeQueue): Pulled 1 entries to queue.
2021.01.12 10:23:17.131568 [ 24079 ] {} <Debug> jason.table_test_local (ReplicatedMergeTreeQueue): Removed 0 entries from queue. Waiting for 0 entries that are currently executing.
2021.01.12 10:23:17.131602 [ 24079 ] {} <Debug> jason.table_test_local: Removing parts.
2021.01.12 10:23:17.131610 [ 24079 ] {} <Debug> jason.table_test_local: Removed 0 parts inside 20191214_0_2_999999999.
2021.01.12 10:23:30.000143 [ 24026 ] {} <Debug> AsynchronousMetrics: MemoryTracking: was 118.81 MiB, peak 177.50 MiB, will set to 118.92 MiB (RSS), difference: -58.58 MiB
2021.01.12 10:23:30.891280 [ 24055 ] {} <Debug> DNSResolver: Updating DNS cache
2021.01.12 10:23:30.891358 [ 24055 ] {} <Debug> DNSResolver: Updated DNS cache
2021.01.12 10:23:45.891516 [ 24040 ] {} <Debug> DNSResolver: Updating DNS cache
2021.01.12 10:23:45.891576 [ 24040 ] {} <Debug> DNSResolver: Updated DNS cache
```

## 5 是否有其他问题
1. drop part 功能需要过滤
2. 需单个shard 所有实例 统一上线
3. 配置更新
