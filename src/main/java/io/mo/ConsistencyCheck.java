package io.mo;

import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

public class ConsistencyCheck {
    private static org.apache.log4j.Logger log = Logger.getLogger(io.mo.ConsistencyCheck.class);
    public static void main(String[] args){
        Properties ini = new Properties();
        try {
            ini.load( new FileInputStream(System.getProperty("prop")));
        } catch (IOException e) {
            log.error("Could not load properties file, please check.");
        }

        String  iDB                 = getProp(ini,"db");
        String  iDriver             = getProp(ini,"driver");
        String  iConn               = getProp(ini,"conn");
        String  iUser               = getProp(ini,"user");
        String  iPassword           = ini.getProperty("password");
        boolean success             = true;

        HashMap<String,String>  errors = new HashMap<>();    
        
        String[] queries = new String[]{
                "(Select w_id, w_ytd from bmsql_warehouse) except (select d_w_id, sum(d_ytd) from bmsql_district group by d_w_id);",
                "(Select d_w_id, d_id, D_NEXT_O_ID - 1 from bmsql_district)  except (select o_w_id, o_d_id, max(o_id) from bmsql_oorder group by  o_w_id, o_d_id);",
                "(Select d_w_id, d_id, D_NEXT_O_ID - 1 from bmsql_district)  except (select no_w_id, no_d_id, max(no_o_id) from bmsql_new_order group by no_w_id, no_d_id);",
                "select * from (select (count(no_o_id)-(max(no_o_id)-min(no_o_id)+1)) as diff from bmsql_new_order group by no_w_id, no_d_id) as temp where diff != 0;",
                "(select o_w_id, o_d_id, sum(o_ol_cnt) from bmsql_oorder  group by o_w_id, o_d_id) except (select ol_w_id, ol_d_id, count(ol_o_id) from bmsql_order_line group by ol_w_id, ol_d_id);",
                "(select d_w_id, sum(d_ytd) from bmsql_district group by d_w_id)  except(Select w_id, w_ytd from bmsql_warehouse);"};

        Properties dbProps = new Properties();
        dbProps.setProperty("user", iUser);
        dbProps.setProperty("password", iPassword);
        Connection conn = null;
        
        try {
            Class.forName(iDriver);
            
            try {
                conn = DriverManager.getConnection(iConn, dbProps);
            }catch (SQLException e){
                log.error("Could not get valid connection from " + iDB + ":\n" +
                        "user=" + iUser + ", password=" + iPassword + "\n" +
                        "jdbcURL="+iConn);
                System.exit(1);
            }
            
            try {
                Statement stmt = conn.createStatement();
                for(String query : queries){
                    ResultSet resultSet = stmt.executeQuery(query);
                    String error = "";
                    int colcount = resultSet.getMetaData().getColumnCount();
                    boolean current = true;
                    while(resultSet.next()){
                        success = false;
                        current = false;
                        for(int i = 1; i < colcount + 1 ; i++){
                            error += resultSet.getString(i);
                            error += "\t";
                        }
                        error += "\n";
                    }

                    if(!current){
                        errors.put(query,error);
                    }
                }

                if(!success){
                    Set keys = errors.keySet();
                    Iterator iterator = keys.iterator();
                    while(iterator.hasNext()){
                        String key = (String) iterator.next();
                        String error = errors.get(key);
                        log.error("Consistency verification failed for sql : " + key);
                        log.error("The exceptional result are :\n" + error);
                    }
                    System.exit(1);
                }else{
                    log.info("Consistency verification successfully.");
                    System.exit(0);
                }
            }catch (SQLException e){
                log.error(e.getMessage());
                success = false;
            }
        } catch (ClassNotFoundException e) {
            log.error("Could not find driver " + iDriver);
            System.exit(1);
        }

    }

    private static String getProp (Properties p, String pName)
    {
        String prop =  p.getProperty(pName);
        log.info(pName + "=" + prop);
        return(prop);
    }
}
