package jdbc.monitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;

import javassist.CannotCompileException;
import javassist.NotFoundException;

public class MonitoringManager {

    private static MonitoringManager _instance;

    public synchronized static MonitoringManager getInstance() {
        if (_instance == null)
            _instance = new MonitoringManager();
        return _instance;
    }

    // Key:Value - Connection:ResourceDataModel
    private HashMap connectionList;

    // Log Writer 
    // TODO: ���߿� Log4J�� �ٲ�� ����...
    private Log log;

    // Key:Value - Statement:ResourceDataModel
    private HashMap resultSetList;

    // Key:Value - Sql String:SQLExecDataModel Class
    private HashMap sqlList;

    // Key:Value - Statement:ResourceDataModel
    private HashMap statementList;

    private MonitoringManager() {
        this.sqlList = new HashMap();
        this.connectionList = new HashMap();
        this.statementList = new HashMap();
        this.resultSetList = new HashMap();
        log = new Log();
    }

    public HashMap getConnectionList() {
        return connectionList;
    }

    public Log getLog() {
        return log;
    }

    public HashMap getResultSetList() {
        return resultSetList;
    }

    public HashMap getSqlList() {
        return this.sqlList;
    }

    public HashMap getStatementList() {
        return statementList;
    }

    public void init() {
        this.initOracle();
        this.initMySql();
    }

    protected void initMySql() {
        ClassFactory factory = new ClassFactory();

        try {
            factory.amendConnection("com.mysql.jdbc.Connection");
            factory.amendStatement("com.mysql.jdbc.Statement");
            factory.amendStatement("com.mysql.jdbc.PreparedStatement");
            factory.amendStatement("com.mysql.jdbc.CallableStatement");
            factory.amendStatement("com.mysql.jdbc.ServerPreparedStatement");
            factory.amendResultSet("com.mysql.jdbc.ResultSet");
        } catch (NotFoundException e) {
            log.println("MonitoringManager.initMySql", "Not Found");
        } catch (CannotCompileException e) {
            log.println("MonitoringManager.initMySql", "Can not Compile " + e.getReason());
        }
    }

    protected void initOracle() {
        ClassFactory factory = new ClassFactory();

        try {
            factory.amendConnection("oracle.jdbc.driver.OracleConnection");
            factory.amendStatement("oracle.jdbc.driver.OracleStatement");
            factory.amendStatement("oracle.jdbc.driver.OraclePreparedStatement");
            factory.amendStatement("oracle.jdbc.driver.OracleCallableStatement");
            factory.amendResultSet("oracle.jdbc.driver.OracleResultSetImpl");
        } catch (NotFoundException e) {
            log.println("MonitoringManager.initOracle", "Not Found");
        } catch (CannotCompileException e) {
            log.println("MonitoringManager.initOracle", "Can not Compile " + e.getReason());
        }
    }

    public void registerConnectionClose(final Connection conn) {
        connectionList.remove(conn);
    }

    public void registerConnectionData(final Connection conn, final String sql) {
        if (conn == null || sql == null)
            return;

        ResourceDataModel connData = (ResourceDataModel) connectionList.get(conn);
        if (connData == null) {
            connData = new ResourceDataModel();
            connData.setClassName(conn.getClass().getName());
            connectionList.put(conn, connData);
        }
        connData.setSql(sql);
        connData.setLatestExecTime(System.currentTimeMillis());
    }

    public void registerExecData(final Statement stmt) {
        String sql = null;
        long time = 0;
        Connection conn = null;
        if (stmt == null)
            return;
        try {
            sql = (String) stmt.getClass().getField("__sqlString").get(stmt);
            time = stmt.getClass().getField("__execTime").getLong(stmt);
            conn = stmt.getConnection();
        } catch (Exception sqle) {
            log.println("MonitoringManager.registerExecData", sqle);
            return;
        }
        if (sql == null)
            return;

        SQLExecDataModel data = null;
        if (sqlList.containsKey(sql)) {
            data = (SQLExecDataModel) sqlList.get(sql);
        } else {
            data = new SQLExecDataModel(sql);
            sqlList.put(sql, data);
        }
        data.registerExec(time);
        // ����ߴٰ� Close �� �����Ѵ�...
        registerConnectionData(conn, sql);
        registerStatementData(stmt, sql);
    }

    public void registerResultSetClose(final ResultSet rset) {
        ResourceDataModel rsetData = (ResourceDataModel) resultSetList.get(rset);
        if (rsetData != null) {
            long elapsed = System.currentTimeMillis() - rsetData.getStartTime();
            SQLExecDataModel data = (SQLExecDataModel) sqlList.get(rsetData.getSql());
            if (data != null)
                data.registerPatch(elapsed);
        }
        resultSetList.remove(rset);
        // Remove Null Key
        resultSetList.remove(null);
    }

    public void registerResultSetData(final Statement stmt, final ResultSet rset) {
        if (stmt == null || rset == null)
            return;

        ResourceDataModel stmtData = (ResourceDataModel) statementList.get(stmt);
        if (stmtData == null)
            return;
        String sql = stmtData.getSql();
        // Oracle Inner Query....
        if (sql.equals("SELECT VALUE FROM NLS_INSTANCE_PARAMETERS WHERE PARAMETER ='NLS_DATE_FORMAT'"))
            return;
        if (sql == null || sql.equals(""))
            return;

        ResourceDataModel rsetData = new ResourceDataModel();
        rsetData.setClassName(rset.getClass().getName());
        rsetData.setSql(sql);
        rsetData.setStartTime(System.currentTimeMillis());

        resultSetList.put(rset, rsetData);
    }

    public void registerStatementClose(final Statement stmt) {
        statementList.remove(stmt);
    }

    public void registerStatementData(final Statement stmt, final String sql) {
        if (stmt == null || sql == null)
            return;

        ResourceDataModel stmtData = (ResourceDataModel) statementList.get(stmt);
        if (stmtData == null) {
            stmtData = new ResourceDataModel();
            stmtData.setClassName(stmt.getClass().getName());
            statementList.put(stmt, stmtData);
        }
        stmtData.setSql(sql);
    }

    public String toString() {
        return sqlList.toString();
    }
}
