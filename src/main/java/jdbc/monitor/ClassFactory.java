package jdbc.monitor;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;

public class ClassFactory {
    Log log = null;

    public ClassFactory() {
        log = MonitoringManager.getInstance().getLog();
    }

    public void amendConnection(String className) throws NotFoundException, CannotCompileException {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));

        CtClass cc;

        cc = cp.get(className);

        // Connection Ÿ���� ��쿡�� �۾��Ѵ�.
        if (!cc.subtypeOf(cp.get("java.sql.Connection"))) {
            log.println("MonitoringManager.amendConnection", className + ":Type(java.sql.Connection) mismatch");
            return;
        }

        CtMethod[] ms = cc.getDeclaredMethods();
        for (int i = 0; i < ms.length; i++) {
            CtMethod m = ms[i];
            if (!Modifier.isPublic(m.getModifiers())) continue;

            // Statement �� �� �Ʒ��� �����ϴ� ��� �޼ҵ带 ã�´�.
            // Ư�� ���⼭�� sql string�� statement�� �����ؾ� �ϱ� ������
            // �μ��� String�� �༮�� ��󳽴�.
            if (m.getReturnType().subtypeOf(cp.get("java.sql.Statement"))) {
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

            // Close�ÿ� ���� �������� �θ� �˸���.
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
        //Delete For Performance...
        cc.detach();
    }

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
}
