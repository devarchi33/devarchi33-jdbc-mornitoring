package jdbc.monitor;

/**
 * 클래스 개요.
 * <ul>
 * <li> 작성일/자: 2005. 9. 9
 * <li> 기능 : JDBC 리소스인 Connection,Statemenet,ResultSet 정보를 담아 두는 데이터 모델이다.
 * </ul>
 *
 * @author 우공이산
 */
public class ResourceDataModel {
    private String className = null;

    private long closeTime = 0L;

    private long latestExecTime = 0L;

    private String sql = null;

    private long startTime = 0L;

    public ResourceDataModel() {
        startTime = System.currentTimeMillis();
        latestExecTime = startTime;
    }

    public String getClassName() {
        return className;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public long getLatestExecTime() {
        return latestExecTime;
    }

    public String getSql() {
        return sql;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setCloseTime(long closeTime) {
        this.closeTime = closeTime;
    }

    public void setLatestExecTime(long latestExecTime) {
        this.latestExecTime = latestExecTime;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
}
