package jdbc.monitor;

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
