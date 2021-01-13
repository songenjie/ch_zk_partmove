package jdbc;

import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class MasterJdbc {
    private static final Logger LOGGER = Logger.getLogger(MasterJdbc.class);
    String HOST;
    String PORT;
    String DRIVER;
    String DB_URL;
    String USER;
    String PASS;
    String DB;
    Connection CONN;
    Statement STMT;

    public MasterJdbc(String host,String username, String password, String driver) {
        HOST = host;
        //PORT = port;
        USER = username;
        PASS = password;
        //DB = database;
        DRIVER = driver;
    }

    public String getDRIVER() {
        return DRIVER;
    }

    public String getDB_URL() {
        return DB_URL;
    }

    public String getUSER() {
        return USER;
    }

    public String getPASS() {
        return PASS;
    }

    public String getDB() {
        return DB;
    }

    public Connection getCONN() {
        return CONN;
    }

    public Statement getSTMT() {
        return STMT;
    }


    public void setDRIVER(String DRIVER) {
        this.DRIVER = DRIVER;
    }

    public void setDB_URL(String DB_URL) {
        this.DB_URL = DB_URL;
    }

    public void setUSER(String USER) {
        this.USER = USER;
    }

    public void setPASS(String PASS) {
        this.PASS = PASS;
    }

    public void setDB(String DB) {
        this.DB = DB;
    }

    public void setCONN(Connection CONN) {
        this.CONN = CONN;
    }

    public void setSTMT(Statement STMT) {
        this.STMT = STMT;
    }

    public ResultSet Select(String sql) {
        ResultSet rs = null;
        try {
            LOGGER.info(DRIVER + "start query : " + sql);
            long begin = System.currentTimeMillis();
            rs=  SelectSql(sql);
            long end = System.currentTimeMillis();
            LOGGER.info("执行（" + sql + "）耗时：" + (end - begin) + "ms");

        } catch (SQLException se) {
            se.printStackTrace();
        }
        return rs;
    }


    public ResultSet SelectSql(String sql) throws SQLException {
        return STMT.executeQuery(sql);
    }


    public void Close() {
        try {
            STMT.close();
            CONN.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

}
