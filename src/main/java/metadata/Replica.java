package metadata;

import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.util.HashMap;
import java.util.List;

public class Replica {
    private static Logger LOGGER = Logger.getLogger(Replica.class);
    String _ReplicaPath;

    //map<parts,lastmodiftime>
    HashMap<String,String> _Parts = new HashMap<String,String>();

    public Replica(String replicaPath) {
        _ReplicaPath = replicaPath;
    }

    public void InitParts(ZooKeeper zooKeeper) {
        LOGGER.info("start init " + _ReplicaPath +  " part  ");
        try {
            List<String> parts = zooKeeper.getChildren(_ReplicaPath+"/parts", false);
            for (String part : parts) {
                LOGGER.info("get reppartlica :" + part);
                part = _ReplicaPath+"/parts/"+part;

                Stat stat = new Stat();
                LOGGER.debug("Reading node " + part);
                zooKeeper.getData(part, false, stat);
                _Parts.put(part,Timer.ConverLongToTIme(stat.getMtime()));
            }
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
        LOGGER.info("start init " + _ReplicaPath +  " part done ");
    }

    public String get_ReplicaPath() {
        return _ReplicaPath;
    }

    public HashMap<String, String> get_Parts() {
        return _Parts;
    }
}
