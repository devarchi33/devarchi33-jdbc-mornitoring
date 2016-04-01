package jdbc.monitor;

import java.io.PrintStream;

public class Log {

    private static final boolean doLog = true;

    private PrintStream out = null;

    public Log() {
        this.out = System.out;
    }

    public void flush(){
        out.flush();
    }

    public void LogWriter(PrintStream out) {
        this.out = out;
    }

    public void println(String position, Exception e) {
        println(position, e.getMessage());
    }
    
    public void println(String position, String log) {
        if (!doLog)
            return;
        out.println(position + ":" + log);
    }
}
