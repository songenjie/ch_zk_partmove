package jdbc;

import metadata.Shard;
import metadata.Table;
import logger.LoggingWatcher;
import org.apache.log4j.Logger;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ClickHouse extends MasterJdbc {

    private static final Logger LOGGER = Logger.getLogger(ClickHouse.class);
    // JDBC driver name and database URL
    static final String JDBC_DRIVER = "ru.yandex.clickhouse.ClickHouseDriver";
    String INSTANCE_CNT;
    String ZOOKEEPER_HOST;

    HashMap<String, Table> TABLES = new HashMap<String,Table>();

    ZooKeeper ZOOKEEPER;

    HashMap<String, HashMap<String, Vector<String>>> _NeedMoveTables  = new HashMap<>();


    public ClickHouse(String host, String username, String password, String instance_cnt, String zookeeperhost) {
        super(host, username, password, JDBC_DRIVER);
        ZOOKEEPER_HOST = zookeeperhost;

        INSTANCE_CNT = instance_cnt;

        String DB_URL = "jdbc:clickhouse://" + host + "/default";
        super.setDB_URL(DB_URL);

        try {
            Class.forName(JDBC_DRIVER);
            LOGGER.info("Connecting to a selected database...");
            super.setCONN(DriverManager.getConnection(super.getDB_URL(), super.getUSER(), super.getPASS()));
            LOGGER.info("Connected database successfully...");

            LOGGER.info("Creating statement...");
            super.setSTMT(super.getCONN().createStatement());
            LOGGER.info("Creating statement successfully...");

        } catch (SQLException se) {
            //Handle errors for JDBC
            se.printStackTrace();
        } catch (Exception e) {
            //Handle errors for Class.forName
            e.printStackTrace();
        }

        InitSystemCluster();
        InitZooKeeper();
        InitTables();

    }

    public void InitSystemCluster() {
        LOGGER.info("Init System Cluster ");
        String sql = "ATTACH TABLE IF NOT EXISTS system.master_tables_dis ON CLUSTER system_cluster AS system.tables" +
                " ENGINE = Distributed('system_cluster', 'system', 'tables', rand())";
        super.Select(sql);
        LOGGER.info("Init System Cluster Done ");
    }

    public void InitZooKeeper() {
        LOGGER.info("Init Zookeeper");
        try {
            ZOOKEEPER = new ZooKeeper(ZOOKEEPER_HOST, 400000, new LoggingWatcher());
        } catch (IOException e) {
            throw new RuntimeException("Cannot connect to source Zookeeper", e);
        }
        LOGGER.info("Init Zookeeper done ");
    }



    public void InitTables() {
        LOGGER.info("Init Shards ");
        try {
            String sql = "select engine_full from (select count(1) as instance_cnt ,engine_full  from " +
                    "system.master_tables_dis where " +
                    "engine='ReplicatedMergeTree'  group by engine_full) where instance_cnt=" + INSTANCE_CNT + ";";

            Vector<String> vtablePaths = new Vector<>();
            ResultSet rs = super.Select(sql);

            //STEP 5: Extract data from result set
            while (rs.next()) {
                String engine_full = rs.getString("engine_full");
                //Display values
                String[]  tablePaths= engine_full.split("'");

                if (!vtablePaths.contains(tablePaths[1]) && tablePaths[1].endsWith("{shard}")) {
                    String tablePath = tablePaths[1].substring(0, tablePaths[1].indexOf("{")-1);
                    LOGGER.info("tablePath : " + tablePath);
                    TABLES.put(tablePath,new Table(tablePath));
                    vtablePaths.add(tablePath);
                }
            }
            rs.close();
        } catch (SQLException se) {
            //Handle errors for JDBC
            se.printStackTrace();
        } catch (Exception e) {
            //Handle errors for Class.forName
            e.printStackTrace();
        }

        int i = 0;
        for (Map.Entry<String, Table> table : TABLES.entrySet()) {

            i++;

            table.getValue().InitShards(ZOOKEEPER);

            _NeedMoveTables.put(table.getKey(),table.getValue().get_NeedMoveShard());
            
            if ( i > 1 )
            {
                return;
            }
            
        }

        LOGGER.info("Init Shards done .. ");
    }

    public void Close() {
        try {
            ZOOKEEPER.close();
            super.Close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    public String getZOOKEEPER_HOST() {
        return ZOOKEEPER_HOST;
    }

    //map<tablename,shards>
    public HashMap<String, HashMap<String, Vector<String>>> get_NeedMoveTables() {
        return _NeedMoveTables;
    }

    public HashMap<String, Table> getTABLES() {
        return TABLES;
    }

    public ZooKeeper getZOOKEEPER() {
        return ZOOKEEPER;
    }
}