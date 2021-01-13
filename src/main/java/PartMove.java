import Node.Node;
import jdbc.AdminServer;
import jdbc.ClickHouse;
import logger.LoggingWatcher;
import metadata.Shard;
import metadata.Table;
import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import picocli.CommandLine;
import zkreader.Reader;
import zkwriter.Writer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "PartMove", showDefaultValues = true)
class PartMove extends TimerTask implements Callable<Void> {
    private static boolean BFIRST = true;
    private static final Logger LOGGER = Logger.getLogger(PartMove.class);
    private static final int DEFAULT_THREADS_NUMBER = 10;
    private static final boolean DEFAULT_COPY_ONLY = false;
    private static final boolean DEFAULT_IGNORE_EPHEMERAL_NODES = true;
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private static final String ADMIN_SERVER_HOST = "";
    private static final String ADMIN_SERVER_USER = "";
    private static final String ADMIN_SERVER_PASS = "";

    private static final String CLICKHOUSE_SOURCE_NAME = "";

    private static final String ZOOKEEPER_TARGET_HOST = "";

    @CommandLine.Option(names = "--help", usageHelp = true, description = "display this help and exit")
    boolean help;

    @CommandLine.Option(names = {"--adminServerHost"},
            paramLabel = "ip:port",
            required = true,
            description = "clickhouse ADMIN_SERVER_HOST")
    String adminServerHost = ADMIN_SERVER_HOST;

    @CommandLine.Option(names = {"--AdminServerUser"},
            paramLabel = "user",
            required = true,
            description = "clickhouse ADMIN_SERVER_USER")
    String AdminServerUser = ADMIN_SERVER_USER;

    @CommandLine.Option(names = {"--AdminServerPass"},
            paramLabel = "passworld",
            required = true,
            description = "clickhouse ADMIN_SERVER_PASS")
    String AdminServerPass = ADMIN_SERVER_PASS;

    @CommandLine.Option(names = {"--clickhouseSourceName"},
            paramLabel = "clustername",
            required = true,
            description = "clickhouse CLICKHOUSE_SOURCE_NAME")
    String clickhouseSourceName = CLICKHOUSE_SOURCE_NAME;

    @CommandLine.Option(names = {"--zookeeperTargetHost"},
            paramLabel = "server:port",
            required = true,
            description = "clickhouse ZOOKEEPER_TARGET_HOST")
    String zookeeperTargetHost = ZOOKEEPER_TARGET_HOST;

    @CommandLine.Option(names = {"-w", "--workers"},
            description = "number of concurrent workers to copy data")
    int workers = DEFAULT_THREADS_NUMBER;

    @CommandLine.Option(names = {"-c", "--copyOnly"},
            description = "set this flag if you do not want to remove nodes that are removed on source",
            arity = "0..1")
    boolean copyOnly = DEFAULT_COPY_ONLY;

    @CommandLine.Option(names = {"-i", "--ignoreEphemeralNodes"},
            description = "set this flag to false if you do not want to copy ephemeral ZNodes",
            arity = "0..1")

    boolean ignoreEphemeralNodes = DEFAULT_IGNORE_EPHEMERAL_NODES;

    @CommandLine.Option(names = {"-m", "--mtime"},
            description = "Ignore nodes older than mtime")
    long mtime = -1;

    @CommandLine.Option(names = {"--timeout"}, description = "Session timeout in milliseconds")
    int sessionTimeout = 40000;

    @CommandLine.Option(names = {"-b", "--batchSize"},
            description = "Batch write operations into transactions of this many operations. "
                    + "Batch sizes are limited by the jute.maxbuffer server-side config, usually around 1 MB.")
    int batchSize = DEFAULT_BATCH_SIZE;

    public PartMove() {
        super();
    }


    @Override
    public void run() {
        try {

            LOGGER.info("using " + workers + " concurrent workers to copy data");
            LOGGER.info("ignore ephemeral nodes = " + String.valueOf(ignoreEphemeralNodes));

            AdminServer adminServer = new AdminServer(adminServerHost, AdminServerUser, AdminServerPass);
            ClickHouse ckMaster = adminServer.CreateCkByClusterName(clickhouseSourceName);

            ZooKeeper zookeeperBackup = new ZooKeeper(zookeeperTargetHost, sessionTimeout, new LoggingWatcher());

            HashMap<String, Table> ckTables = ckMaster.getTABLES();

            if (BFIRST) {
                copyOnly = false;
                BFIRST = false;
                FirstJob(ckTables, ckMaster, zookeeperBackup);
            } else {
                copyOnly = true;
                OtherJob(ckTables, ckMaster, zookeeperBackup);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        CommandLine.call(new PartMove(), System.err, args);
    }

    public Void call() throws Exception {
        Timer timer = new Timer();
        long period = (long) 1 * 24 * 60 * 60 * 1000;
        long delay = (long) 0;
        timer.schedule(new PartMove(), delay, period);
        return  null;
    }



    public Stat createDeepthNode(ZooKeeper zooKeeper, String path) {
        Stat state = null;
        try {
            String[] nodes = path.split("/");
            String deeppath = "";
            String Level = "/";
            for (int i = 1; i < nodes.length; i++) {
                deeppath += Level + nodes[i];
                LOGGER.debug("start copy znode :" + deeppath);
                state = zooKeeper.exists(deeppath, false);
                if (state == null) {
                    zooKeeper.create(deeppath, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                }
            }
            return zooKeeper.exists(path, false);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
        return state;
    }


    private String zkPath(String addr) {
        return '/' + addr.split("/", 2)[1];
    }

    private void FirstJob(HashMap<String, Table> ckTables, ClickHouse ckMaster, ZooKeeper zookeeperBackup) throws KeeperException, InterruptedException {
        Stat statbackup;

        for (Table cktable : ckTables.values()) {
            HashMap<String, Shard> ckshards = cktable.get_Shards();
            for (Shard shard : ckshards.values()) {
                //copy shard
                String shardpath = shard.get_ShardPath() +"/replicas";

                LOGGER.info("source of shard path  : " + shardpath);
                Reader reader = new Reader(ckMaster.getZOOKEEPER_HOST() + shardpath, workers, sessionTimeout);
                Node root = reader.read();

                if (root != null) {
                    LOGGER.info("start copy znode :" + shardpath);

                    if (zookeeperBackup.exists(shardpath, false) == null) {
                        createDeepthNode(zookeeperBackup, shardpath);
                    }

                    LOGGER.info("target of shard path " + zookeeperTargetHost + shardpath);
                    Writer writer = new Writer(zookeeperBackup, zkPath(zookeeperTargetHost + shardpath), root, copyOnly, ignoreEphemeralNodes, mtime, batchSize);
                    writer.write();

                    //drop hot replica from backup
                    Vector<String> hotParts = shard.get_NeedDropParts();
                    LOGGER.info("hot parts size " + hotParts.size());

                    for (String part : hotParts) {
                        LOGGER.info("drop hot part from targe path : " + part);
                        statbackup = zookeeperBackup.exists(part, false);
                        if (statbackup != null) {
                            zookeeperBackup.delete(part, statbackup.getVersion());
                        }
                    }


                    //drop cold replica for master
                    Vector<String> coldParts = shard.get_NeedMoveParts();
                    for (String part : coldParts) {
                        LOGGER.info("drop cold  part from source path : " + part);
                        statbackup = ckMaster.getZOOKEEPER().exists(part, false);
                        if (statbackup != null) {
                            ckMaster.getZOOKEEPER().delete(part, statbackup.getVersion());
                        }
                    }

                } else {
                    LOGGER.error("FAILED");
                }
            }
        }
    }

    private void OtherJob(HashMap<String, Table> ckTables, ClickHouse ckMaster, ZooKeeper zookeeperBackup) {
        for (Table cktable : ckTables.values()) {
            HashMap<String, Shard> ckshards = cktable.get_Shards();
            for (Shard shard : ckshards.values()) {
                //n fist
                Vector<String> coldParts = shard.get_NeedMoveParts();
                for (String part : coldParts) {
                    LOGGER.info("source of parts path  : " + part);
                    Reader reader = new Reader(ckMaster.getZOOKEEPER_HOST() + part, workers, sessionTimeout);
                    Node root = reader.read();
                    if (root != null) {
                        LOGGER.info("target of part path " + zookeeperTargetHost + part);
                        Writer writer = new Writer(zookeeperBackup, zkPath(zookeeperTargetHost + part), root, copyOnly, ignoreEphemeralNodes, mtime, batchSize);
                        writer.write();
                    } else {
                        LOGGER.error("FAILED");
                    }
                }
            }
        }
    }

}
