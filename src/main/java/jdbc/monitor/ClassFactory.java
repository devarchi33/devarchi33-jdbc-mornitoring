package jdbc.monitor;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;

/**
 * javassist 를 이용하여 ClassPool 생성하고, CtClass 를 구하여 직접 bytecode 를 삽입하는 기능을 정의한다.
 */
public class ClassFactory {
    Log log = null;

    public ClassFactory() {
        log = MonitoringManager.getInstance().getLog();
    }

    /**
     * @param className
     * @throws NotFoundException      : Signals that something could not be found.
     * @throws CannotCompileException : Thrown when bytecode transformation has failed.
     */
    public void amendConnection(String className) throws NotFoundException, CannotCompileException {
        /**
         * 싱글톤 팩토리 ClassPool 로 부터 ClassPool 획득 후
         * 현재 객체의 ClassPath 를 ClassPool 에 주입한다.
         */
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));

        CtClass cc;

        /**
         * 획득한 ClassPool 에서 인자로 넘어온 클래스 이름을 갖는 CtClass 객체를 획득한다
         */
        cc = cp.get(className);

        /**
         * 획득한 CtClass 객체의 타입이 java.sql.Connection 이 아니면 리턴한다.
         */
        if (!cc.subtypeOf(cp.get("java.sql.Connection"))) {
            log.println("MonitoringManager.amendConnection", className + ":Type(java.sql.Connection) mismatch");
            return;
        }

        /**
         * 획득한 CtClass 로 부터 메소드들을 구하여 CtMethod 배열에 담는다.
         */
        CtMethod[] ms = cc.getDeclaredMethods();

        /**
         * 획득한 메소드을을 순회 하면서 public 지정자가 아니면 넘어가고,
         * public 지정자를 갖고 있으면 리턴 타입이 java.sql.Statement 인지 조사한다.
         */
        for (int i = 0; i < ms.length; i++) {
            CtMethod m = ms[i];
            if (!Modifier.isPublic(m.getModifiers())) continue;

            if (m.getReturnType().subtypeOf(cp.get("java.sql.Statement"))) {

                /**
                 * 리턴 타입이 java.sql.Statement 이면 파라미터를 구하여 CtClass 배열에 담는다.
                 * 메소드 리턴 전에 코드를 삽입한다
                 */
                CtClass[] paras = m.getParameterTypes();
                if (paras.length > 0) {
                    if (paras[0].getName().endsWith("String")) {
                        String code = "{ "
                                + " $_.getClass().getField(\"__sqlString\").set($_, $1); "
                                + " }";
                        m.insertAfter(code);
                        log.println("MonitoringManager.amendConnection", className + ":" + m.getName() + "-> Successfully Amended!");
                    }
                }
            }

            /**
             * 메소드 이름이 close 일 경우 코드 삽입.
             */
            if (m.getName().equals("close")) {
                String code = "{ "
                        + " jdbc.monitor.MonitoringManager manager = jdbc.monitor.MonitoringManager.getInstance(); "
                        + " manager.registerConnectionClose(this); "
                        + "}";
                m.insertAfter(code);
                log.println("MonitoringManager.amendConnection", className + ":" + m.getName() + "-> Successfully Amended!");
            }
        }
        cc.toClass(this.getClass().getClassLoader());

        /**
         * CtClass 해제. 주의! CtClass 조작 이후 CtClass 를 해제 하지 않으면 메모리 leak 발생.
         */
        cc.detach();
    }
    //.amendConnection() 종료.

    public void amendStatement(String className) throws NotFoundException, CannotCompileException {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));

        CtClass cc;

        cc = cp.get(className);

        // Statement Ÿ���� ��쿡�� �۾��Ѵ�.
        if (!cc.subtypeOf(cp.get("java.sql.Statement"))) {
            log.println("MonitoringManager.amendStatement", className + ":Type(java.sql.Statement) mismatch");
            return;
        }

        CtMethod[] ms = cc.getDeclaredMethods();

        // Field �ɱ�....
        // ��ӹ��� �� �����Ƿ�, ������ �� �����...
        CtField cf;
        try {
            cf = cc.getDeclaredField("__isExecuted");//���� Ŭ�������� ã�´�...
        } catch (NotFoundException nfe) {
            cf = new CtField(CtClass.booleanType, "__isExecuted", cc);
            cf.setModifiers(Modifier.PRIVATE);
            cc.addField(cf, "false");
            log.println("MonitoringManager.amendStatement", className + ":__isExecuted Is Successfully Added!");
        }
        try {
            cf = cc.getField("__sqlString");//���� Ŭ�������� ã�´�...
        } catch (NotFoundException nfe) {
            cf = new CtField(ClassPool.getDefault().get("java.lang.String"), "__sqlString", cc);
            cf.setModifiers(Modifier.PUBLIC);
            cc.addField(cf, "null");
            log.println("MonitoringManager.amendStatement", className + ":__sqlString Is Successfully Added!");
        }
        try {
            cf = cc.getField("__execTime");//���� Ŭ�������� ã�´�...
        } catch (NotFoundException nfe) {
            cf = new CtField(CtClass.longType, "__execTime", cc);
            cf.setModifiers(Modifier.PUBLIC);
            cc.addField(cf, "0L");
            log.println("MonitoringManager.amendStatement", className + ":__execTime Is Successfully Added!");
        }

        for (int i = 0; i < ms.length; i++) {
            CtMethod m = ms[i];
            if (!Modifier.isPublic(m.getModifiers())) continue;
            //Execute Method
            if (m.getName().startsWith("execute")) {
                CtClass[] paras = m.getParameterTypes();
                String before = " if(!__isExecuted) { "
                        + "    __execTime = System.currentTimeMillis();"
                        + "    __isExecuted = true; "
                        + " }";
                m.insertBefore(before);
                String after = "";
                if (paras.length > 0) {
                    if (paras[0].getName().endsWith("String")) {
                        after = "  {"
                                + " this.__sqlString = $1;"
                                + "}";
                    }
                }
                after += "if(__isExecuted) { "
                        + "  __execTime = System.currentTimeMillis() - __execTime;"
                        + "  jdbc.monitor.MonitoringManager manager = jdbc.monitor.MonitoringManager.getInstance(); "
                        + "  manager.registerExecData(this); "
                        + "  __isExecuted = false; "
                        + "}"
                ;
                m.insertAfter(after);
                log.println("MonitoringManager.amendStatement", className + ":" + m.getName() + "Is Successfully Amended!");
            }
            // Close Method
            if (m.getName().startsWith("close")) {
                CtClass[] paras = m.getParameterTypes();
                if (paras.length == 0) {
                    String code =
                            " { "
                                    + " jdbc.monitor.MonitoringManager manager = jdbc.monitor.MonitoringManager.getInstance(); "
                                    + " manager.registerStatementClose(this); "
                                    + "}";

                    m.insertBefore(code);
                    log.println("MonitoringManager.amendStatement", className + ":" + m.getName() + "Is Successfully Amended!");
                }
            }
            // Resultset�� �����ϴ� �޼ҵ� ó��...
            //if (m.getReturnType() == ClassPool.getDefault().get("java.sql.ResultSet")) {
            if (m.getName().equals("executeQuery") || m.getName().equals("getResultSet")) {
                //System.out.println("ResultSet Return:" + m.getName());
                String code =
                        " { "
                                + " jdbc.monitor.MonitoringManager manager = jdbc.monitor.MonitoringManager.getInstance(); "
                                //+ " System.out.println(this.__sqlString + $0 + $_.getClass().getName() ); "
                                + " manager.registerResultSetData(this,$_); "
                                + "}";

                m.insertAfter(code);
                log.println("MonitoringManager.amendStatement", className + ":" + m.getName() + "Is Successfully Amended!");
            }
        }
        cc.toClass(this.getClass().getClassLoader());

        //Delete For Performance...
        cc.detach();
    }
    //.amendStatement() 종료.

    public void amendResultSet(String className) throws NotFoundException, CannotCompileException {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));

        CtClass cc;

        cc = cp.get(className);

        // ResultSet Ÿ���� ��쿡�� �۾��Ѵ�.
        if (!cc.subtypeOf(cp.get("java.sql.ResultSet"))) {
            log.println("MonitoringManager.amendResultSet", "jType(java.sql.ResultSet) mismatch");
            return;
        }


        CtMethod m = cc.getDeclaredMethod("close");
        String code = "{ "
                + " jdbc.monitor.MonitoringManager manager = jdbc.monitor.MonitoringManager.getInstance(); "
                + " manager.registerResultSetClose(this); "
                + "}";
        m.insertAfter(code);
        log.println("MonitoringManager.amendStatement", className + ":" + m.getName() + "Is Successfully Amended!");

        cc.toClass(this.getClass().getClassLoader());

        //Delete For Performance...
        cc.detach();
    }
    //. amendResultSet 종료.
}
