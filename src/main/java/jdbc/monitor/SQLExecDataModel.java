package jdbc.monitor;

public class SQLExecDataModel {

    private long avgExecTime = 0;

    private long avgPatchTime = 0;

    // ��� ������.
    // ���࿡ ���õ� ��� ���� - Statement.execute ���ۿ��� �����
    private long callCount = 0;

    private long latestCallTime = 0;

    private long latestPatchTime = 0;

    private long maxExecTime = 0;

    private long maxPatchTime = 0;

    private long minExecTime = 0;

    private long minPatchTime = 0;

    // ��ġ�� ���õ� ��� ���� - ResultSet�������� close����
    private long patchCount = 0;

    private String sql = null;

    public SQLExecDataModel(String sql) {
        this.sql = sql;
    }

    public long getAvgExecTime() {
        return avgExecTime;
    }

    public long getAvgPatchTime() {
        return avgPatchTime;
    }

    public long getCallCount() {
        return callCount;
    }

    public long getLatestCallTime() {
        return latestCallTime;
    }

    public long getMaxExecTime() {
        return maxExecTime;
    }

    public long getMaxPatchTime() {
        return maxPatchTime;
    }

    public long getMinExecTime() {
        return minExecTime;
    }

    public long getMinPatchTime() {
        return minPatchTime;
    }

    public long getPatchCount() {
        return patchCount;
    }

    public String getSql() {
        return sql;
    }

    public synchronized void registerExec(long execTime) {
        latestCallTime = System.currentTimeMillis();

        callCount++;
        if (callCount == 1)
            minExecTime = execTime;

        if (execTime > maxExecTime)
            maxExecTime = execTime;
        if (execTime < minExecTime)
            minExecTime = execTime;

        avgExecTime = (long) (avgExecTime * (callCount - 1) + execTime) / callCount;
    }

    public synchronized void registerPatch(long patchTime) {
        latestPatchTime = System.currentTimeMillis();

        patchCount++;
        if (patchCount == 1)
            minPatchTime = patchTime;

        if (patchTime > maxPatchTime)
            maxPatchTime = patchTime;
        if (patchTime < minPatchTime)
            minPatchTime = patchTime;

        avgPatchTime = (long) (avgPatchTime * (patchCount - 1) + patchTime) / patchCount;
    }

    public String toString() {
        return this.sql;
    }
}
