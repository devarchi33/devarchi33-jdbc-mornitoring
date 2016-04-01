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
 * 클래스 개요.
 * <ul>
 * <li> 작성일/자: 2005. 9. 9 - 김종호
 * <li> 기능 : 모니터링을 위해 JDBC 리소스인 Connection, Statement, ResultSet을 바이너리 수준에서 수정한다.
 * 삽입되는 코드는 실행에 최소한의 영향을 준다.
 * </ul>
 *
 * @author 우공이산
 */

// Ctrl-Shift-F 클릭하지 마라...소스 코드 허뜨려진다..
public class ClassFactory {
    Log log = null;
    public ClassFactory() {
        log = MonitoringManager.getInstance().getLog();
    }

    /**
     * @param className
     * @throws NotFoundException
     * @throws CannotCompileException
     * 수정 하는 부분
     * <ul>
     * <li> 리턴 타입이 java.sql.Statement이며 첫번째 인자가 String 이면, 이는 예를 들면 
     * <code>PreparedStatement prepareStatement(String sql)</code> 인데...
     * 이 메소드가 리턴하는 Statement에 심어논 __sqlString에 sql 문을 저장한다.
     * 나중에 execute시에 이 __sqlString을 쓸 것임.
     * <li> close 메소드에 닫혔음을 등록하는 코드를 삽입한다.
     * </ul>
     */
    public void amendConnection(String className) throws NotFoundException, CannotCompileException {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));
        
        CtClass cc;
        
        cc = cp.get(className);
        
        // Connection 타입일 경우에만 작업한다.
        if(! cc.subtypeOf(cp.get("java.sql.Connection"))){
            log.println("MonitoringManager.amendConnection",className + ":Type(java.sql.Connection) mismatch");
            return;
        }
        
        CtMethod[] ms = cc.getDeclaredMethods();
        for (int i = 0; i < ms.length; i++) {
            CtMethod m = ms[i];
            if(! Modifier.isPublic(m.getModifiers())) continue;
            
            // Statement 및 그 아류를 리턴하는 모든 메소드를 찾는다.
            // 특히 여기서는 sql string을 statement에 저장해야 하기 때문에
            // 인수가 String인 녀석을 골라낸다.
            if (m.getReturnType().subtypeOf(cp.get("java.sql.Statement"))) {
                CtClass[] paras = m.getParameterTypes();
                if (paras.length > 0) {
                    if (paras[0].getName().endsWith("String")) {
                        String code = "{ "
                                    + " $_.getClass().getField(\"__sqlString\").set($_, $1); "
                                    + " }";
                        m.insertAfter(code);
                        log.println("MonitoringManager.amendConnection",className + ":" + m.getName() + "-> Successfully Amended!");
                    }
                }
            }
            
            // Close시에 내가 닫혔음을 널리 알린다.
            if(m.getName().equals("close")){
                String code = "{ "
                            + " jdbc.monitor.MonitoringManager manager = jdbc.monitor.MonitoringManager.getInstance(); "
                            + " manager.registerConnectionClose(this); "
                            + "}";
                m.insertAfter(code);
                log.println("MonitoringManager.amendConnection",className + ":" + m.getName() + "-> Successfully Amended!");
            }
        }
        cc.toClass(this.getClass().getClassLoader());
        //Delete For Performance...
        cc.detach();
    }
    
    /**
     * @param className
     * @throws NotFoundException
     * @throws CannotCompileException
     * 수정 하는 부분
     * <ul>
     * <li> private __isExecuted라는 멤버변수를 설정하여 상속에 의한 두번 실행을 예방한다.
     * <li> public __sqlString라는 멤버 변수를 설정하여 해당 Statement가 실행한 SQL을 저장한다. 
     * SQL은 크게 두 곳에서 설정이 되는데 Statement를 얻을 올 때 예를 들면 
     * <code>PreparedStatement Connection.prepareStatement(String sql)</code>과 같이 Connection단에서나
     * 아니면 <code>ResultSet Statement.executeQuery(String sql)  </code>과 같이 Statement 내부 메서드에서 이다.
     * 각각 설정하는 곳이 다르므로 유의해야 한다.
     * <li> public __execTime에는 실행된 시간이 들어간다. 물론 MonitoringManager를 통해서도 할 수 있지만, 속도를 위해 
     * 이 방법을 썼다.
     * <li> execute로 시작하고 인수가 String인 메소드는 Sql을 실행하는 것이므로 여기에 맞는 코드를 삽입한다.
     * <li> ResultSet을 리턴하는 메소드 2개에 ResultSet을 등록하는 코드를 삽입한다.
     * <li> close 메소드에 닫혔음을 등록하는 코드를 삽입한다.
     * </ul>
     */
    public void amendStatement(String className) throws NotFoundException, CannotCompileException {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));
        
        CtClass cc;
        
        cc = cp.get(className);
        
        // Statement 타입일 경우에만 작업한다.
        if(! cc.subtypeOf(cp.get("java.sql.Statement"))){
            log.println("MonitoringManager.amendStatement",className + ":Type(java.sql.Statement) mismatch");
            return;
        }
        
        CtMethod[] ms = cc.getDeclaredMethods();

        // Field 심기....
        // 상속받을 수 있으므로, 있으면 안 만든다...
        CtField cf;
        try {
            cf = cc.getDeclaredField("__isExecuted");//하위 클래스까지 찾는다...
        } catch (NotFoundException nfe) {
            cf = new CtField(CtClass.booleanType, "__isExecuted", cc);
            cf.setModifiers(Modifier.PRIVATE);
            cc.addField(cf, "false");
            log.println("MonitoringManager.amendStatement",className + ":__isExecuted Is Successfully Added!");
        }
        try {
            cf = cc.getField("__sqlString");//하위 클래스까지 찾는다...
        } catch (NotFoundException nfe) {
            cf = new CtField(ClassPool.getDefault().get("java.lang.String"), "__sqlString", cc);
            cf.setModifiers(Modifier.PUBLIC);
            cc.addField(cf, "null");
            log.println("MonitoringManager.amendStatement",className + ":__sqlString Is Successfully Added!");
        }
        try {
            cf = cc.getField("__execTime");//하위 클래스까지 찾는다...
        } catch (NotFoundException nfe) {
            cf = new CtField(CtClass.longType, "__execTime", cc);
            cf.setModifiers(Modifier.PUBLIC);
            cc.addField(cf, "0L");
            log.println("MonitoringManager.amendStatement",className + ":__execTime Is Successfully Added!");
        }

        for (int i = 0; i < ms.length; i++) {
            CtMethod m = ms[i];
            if(! Modifier.isPublic(m.getModifiers())) continue;
            //Execute Method
            if (m.getName().startsWith("execute")) {
                CtClass[] paras = m.getParameterTypes();
                String before = " if(!__isExecuted) { "
                              + "    __execTime = System.currentTimeMillis();"
                              + "    __isExecuted = true; "
                              + " }"
                              ;
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
                     +  "  __execTime = System.currentTimeMillis() - __execTime;"
                     +  "  jdbc.monitor.MonitoringManager manager = jdbc.monitor.MonitoringManager.getInstance(); "
                     +  "  manager.registerExecData(this); "
                     +  "  __isExecuted = false; "
                     +  "}"
                    ;
                m.insertAfter(after);
                log.println("MonitoringManager.amendStatement",className + ":" + m.getName() + "Is Successfully Amended!");
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
                    log.println("MonitoringManager.amendStatement",className + ":" + m.getName() + "Is Successfully Amended!");
                }
            }
            // Resultset을 리턴하는 메소드 처리...
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
                log.println("MonitoringManager.amendStatement",className + ":" + m.getName() + "Is Successfully Amended!");
            }
        }
        cc.toClass(this.getClass().getClassLoader());
        
        //Delete For Performance...
        cc.detach();
    }
    
    /**
     * @param className
     * @throws NotFoundException
     * @throws CannotCompileException
     * 수정 하는 부분
     * <ul>
     * <li> close 메소드에 닫혔음을 등록하는 코드를 삽입한다.
     * </ul>
     */
    public void amendResultSet(String className) throws NotFoundException, CannotCompileException {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(this.getClass()));
        
        CtClass cc;
        
        cc = cp.get(className);

        // ResultSet 타입일 경우에만 작업한다.
        if(! cc.subtypeOf(cp.get("java.sql.ResultSet"))){
            log.println("MonitoringManager.amendResultSet","jType(java.sql.ResultSet) mismatch");
            return;
        }

        
        CtMethod m = cc.getDeclaredMethod("close");
        String code = "{ "
                    + " jdbc.monitor.MonitoringManager manager = jdbc.monitor.MonitoringManager.getInstance(); "
                    + " manager.registerResultSetClose(this); "
                    + "}";
        m.insertAfter(code);
        log.println("MonitoringManager.amendStatement",className + ":" + m.getName() + "Is Successfully Amended!");

        cc.toClass(this.getClass().getClassLoader());
        
        //Delete For Performance...
        cc.detach();
    }
}
