package metadata;

import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.util.*;


public class Shard {
    private static Logger LOGGER = Logger.getLogger(Shard.class);
    String _TimerLastDayTime;

    public Shard(String shardpath) {
        _ShardPath = shardpath;
        _TimerLastDayTime = Timer.GetLasteDayTime();
    }

    String _ShardPath;

    Vector<String> _ParitionCold = new Vector<String>();
    Vector<String> _ParitionHolt = new Vector<String>();

    //all shard parts
    Vector<String> _NeedMoveParts = new Vector<String>();
    Vector<String> _NeedDropParts = new Vector<String>();

    HashMap<String, Replica> _Replicas = new HashMap<String, Replica>();
    HashMap<String, Parition> _Partitions = new HashMap<String, Parition>();

    //定时任务
    void JudgeAllParitions() {
        for (Parition parition : _Partitions.values()) {
            if (_TimerLastDayTime.compareTo(parition.getLastModiftime()) >= 0) {
                parition._State = State.COLD;
                _ParitionCold.add(parition.get_ParitionID());
            } else if (parition.get_ParitionID().compareTo(_ShardPath + "/block_numbers/20191216") <= 0) {
                parition._State = State.COLD;
                _ParitionCold.add(parition.get_ParitionID());
            } else {
                parition._State = State.HOT;
                _ParitionHolt.add(parition.get_ParitionID());
            }
            
            LOGGER.info("partition : " + parition.get_ParitionID() + " time: + " + parition.getLastModiftime() + " state: " + parition._State);
        }
    }


    void InitColdReplicas() {
        for (String parttion : _ParitionCold) {
            LOGGER.info("cold partition :" + parttion);
        }
        for (Replica replica : _Replicas.values()) {
            for (String part : replica.get_Parts().keySet()) {

                String[] spart = part.split("/");
                String partnumber = spart[spart.length - 1];
                partnumber = partnumber.substring(0, partnumber.indexOf("_"));
                LOGGER.info("partnumber : " + partnumber);

                boolean bfind = false;

                for (String parition : _ParitionCold) {
                    if (parition.contains(partnumber)) {
                        LOGGER.info("parts : " + part + " need move ");
                        _NeedMoveParts.add(part);
                        bfind = true;
                        break;
                    }
                }
                if (!bfind) {
                    LOGGER.info("parts : " + part + " need drop ");
                    _NeedDropParts.add(part);
                }
            }
        }
    }


    void CreateParitionStateNode(ZooKeeper zooKeeper) {
        try {
            Stat stat = new Stat();
            for (String znode : _ParitionHolt) {
                LOGGER.info("start set " + znode + "/State to " + State.HOT);
                stat = zooKeeper.exists(znode + "/State", false);
                if (stat == null) {
                    zooKeeper.create(znode + "/State", "HOT".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                } else {
                    zooKeeper.setData(znode + "/State", "HOT".getBytes(), stat.getVersion());
                }
                LOGGER.info("set " + znode + "/State to " + State.HOT + " done ");
            }
            for (String znode : _ParitionCold) {
                LOGGER.info("start set " + znode + "/State to " + State.COLD);
                stat = zooKeeper.exists(znode + "/State", false);
                if (stat == null) {
                    zooKeeper.create(znode + "/State", "COLD".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                } else {
                    zooKeeper.setData(znode + "/State", "COLD".getBytes(), stat.getVersion());
                }
                LOGGER.info("start set " + znode + "/State to " + State.COLD + " done ");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    void UpdateParition(String part, String datetime) {
        LOGGER.info("part " + part + " date time is " + datetime);
        for (Map.Entry<String, Parition> paritions : _Partitions.entrySet()) {
            if (part.contains(paritions.getKey()) && datetime.compareTo(paritions.getValue().LastModiftime) > 0) {
                paritions.getValue().setLastModiftime(datetime);
                LOGGER.info("update parition time to " + datetime);
            }
        }
    }


    public void InitParitions(ZooKeeper zooKeeper) {
        LOGGER.info("start init " + _ShardPath + "  paritions");
        try {
            List<String> paritions = zooKeeper.getChildren(_ShardPath + "/block_numbers", false);
            for (String parition : paritions) {
                LOGGER.info("get parition :" + parition);
                _Partitions.put(parition, new Parition(_ShardPath + "/block_numbers/" + parition));
            }

        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
        LOGGER.info("start init " + _ShardPath + "  paritions done ");
    }


    public void InitReplicas(ZooKeeper zooKeeper) {
        LOGGER.info("start init " + _ShardPath + " replicas  ");
        try {
            List<String> replicas = zooKeeper.getChildren(_ShardPath + "/replicas", false);
            for (String replica : replicas) {
                LOGGER.info("get replica :" + replica);
                _Replicas.put(replica, new Replica(_ShardPath + "/replicas/" + replica));
            }

        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
        for (Replica replica : _Replicas.values()) {
            replica.InitParts(zooKeeper);
        }

        LOGGER.info("start init " + _ShardPath + "  replicas done ");
    }

    public void ResetParitionsByReplicas() {
        for (Replica replica : _Replicas.values()) {
            for (Map.Entry<String, String> part : replica.get_Parts().entrySet()) {
                UpdateParition(part.getKey(), part.getValue());
            }
        }
    }

    public String get_ShardPath() {
        return _ShardPath;
    }

    public HashMap<String, Parition> get_Partitions() {
        return _Partitions;
    }

    public HashMap<String, Replica> get_Replicas() {
        return _Replicas;
    }

    public Vector<String> get_NeedMoveParts() {
        return _NeedMoveParts;
    }

    public Vector<String> get_NeedDropParts() {
        return _NeedDropParts;
    }
}



