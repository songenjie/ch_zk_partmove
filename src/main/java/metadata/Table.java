package metadata;

import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class Table {
    private static Logger LOGGER = Logger.getLogger(Table.class);
    String _TablePath;

    //map<shardnum,replicas>
    HashMap<String, Vector<String>> _NeedMoveShard = new HashMap<>();

    public Table(String tablePath) {
        _TablePath = tablePath;
    }

    HashMap<String, Shard> _Shards = new HashMap<String, Shard>();

    public void InitShards(ZooKeeper zooKeeper) {
        LOGGER.info("start init " + _TablePath + "  Shards");
        try {
            List<String> shardNumbers = zooKeeper.getChildren(_TablePath, false);

            for (String shardnum : shardNumbers) {
                LOGGER.info("get shardnum :" + shardnum);
                _Shards.put(shardnum, new Shard(_TablePath + "/" + shardnum));
            }
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }


        int i = 0;
        for (Map.Entry<String, Shard> mShard : _Shards.entrySet()) {
            mShard.getValue().InitParitions(zooKeeper);
            mShard.getValue().InitReplicas(zooKeeper);
            mShard.getValue().ResetParitionsByReplicas();
            mShard.getValue().JudgeAllParitions();
            mShard.getValue().CreateParitionStateNode(zooKeeper);
            mShard.getValue().InitColdReplicas();

            _NeedMoveShard.put(mShard.getKey(), mShard.getValue().get_NeedMoveParts());

            i++;
            if (i > 2) {
                return ;
            }
        }

        LOGGER.info("start init " + _TablePath + "  Shards done ");
    }

    public HashMap<String, Vector<String>> get_NeedMoveShard() {
        return _NeedMoveShard;
    }

    public HashMap<String, Shard> get_Shards() {
        return _Shards;
    }
}
