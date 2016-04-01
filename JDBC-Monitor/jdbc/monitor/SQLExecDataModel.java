package jdbc.monitor;

/**
 * 클래스 개요.
 * <ul>
 * <li> 작성일/자: 2005. 9. 9
 * <li> 기능 : SQL 실행에 관련된 정보를 저장하는 데이터 모델이다. 여기에는 Statement executeXXX 메소드의 시작에서
 * 종료까지 정보를 Exec관련 필드에, ResulteSet 생성에서 종료까지 정보를 Patch관련 필드에 저장한다.
 * </ul>
 * 
 * @author 우공이산
 */
public class SQLExecDataModel {

    private long avgExecTime = 0;

    private long avgPatchTime = 0;

    // 통계 데이터.
    // 실행에 관련된 통계 정보 - Statement.execute 시작에서 종료까
    private long callCount = 0;

    private long latestCallTime = 0;

    private long latestPatchTime = 0;

    private long maxExecTime = 0;

    private long maxPatchTime = 0;

    private long minExecTime = 0;

    private long minPatchTime = 0;

    // 패치에 관련된 통계 정보 - ResultSet생성에서 close까지
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
