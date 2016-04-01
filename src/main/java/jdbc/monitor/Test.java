package jdbc.monitor;

public class Test {

    /**
     * @param args
     */
    public static void main(String[] args) {
        MonitoringManager manager = MonitoringManager.getInstance();
        manager.init();
    }
}
