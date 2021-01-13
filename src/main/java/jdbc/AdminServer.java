package jdbc;

import org.apache.log4j.Logger;
import org.apache.zookeeper.ZooKeeper;

import java.sql.*;


public class AdminServer extends MasterJdbc {

    private static final Logger LOGGER = Logger.getLogger(AdminServer.class);

    //static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    String[] zookeepers;

    public AdminServer(String host ,String username, String password) {
        super(host,username, password, JDBC_DRIVER);


        String DB_URL = "jdbc:mysql://" + host + "/adminserver";
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
    }

    public ClickHouse CreateCkByClusterName(String ClusterName) {
        String zookeeperHost = GetClusterZk(ClusterName);

        String sql = "select domain, http_port ,admin_name, admin_password, instance_cnt " +
                "from clusters where name='" + ClusterName + "'";
        try {
            ResultSet rs= super.Select(sql);
            //STEP 5: Extract data from result set
            while (rs.next()) {

                String domain = rs.getString("domain");
                String http_port = rs.getString("http_port");
                String admin_name = rs.getString("admin_name");
                String admin_password = rs.getString("admin_password");
                String  instance_cnt = String.valueOf(rs.getLong("instance_cnt"));

                admin_password="jd_olap";

                //Display values
                LOGGER.info("domain\t: " + domain);
                LOGGER.info("http_port\t: " + http_port);
                LOGGER.info("admin_name\t: " + admin_name);
                LOGGER.info("admin_password\t: " + admin_password);
                LOGGER.info("instance_cnt\t: " + instance_cnt);

                rs.close();
                return new ClickHouse(domain+":"+http_port,admin_name,admin_password,instance_cnt,zookeeperHost);
            }
            rs.close();
        } catch (SQLException se) {
            //Handle errors for JDBC
            se.printStackTrace();
        } catch (Exception e) {
            //Handle errors for Class.forName
            e.printStackTrace();
        }
        return null;
    }

    public String GetClusterZk(String ClusterName) {

        String sql = "select zk_addrs from clusters where name='" + ClusterName + "'";
        try {
            ResultSet rs= super.Select(sql);
            //STEP 5: Extract data from result set
            while (rs.next()) {
                String zk_addrs = rs.getString("zk_addrs");
                //Display values
                LOGGER.info("ID: " + zk_addrs);

                zookeepers = zk_addrs.split(",");
            }
            rs.close();
            return zookeepers[0];
        } catch (SQLException se) {
            //Handle errors for JDBC
            se.printStackTrace();
        } catch (Exception e) {
            //Handle errors for Class.forName
            e.printStackTrace();
        }
        return "";
    }


    public void Close() {
        super.Close();
    }
}
