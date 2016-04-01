package jdbc.monitor.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jdbc.monitor.MonitoringManager;

/**
 * Ŭ���� ����.
 * <ul>
 * <li> �ۼ���/��: 2005. 9. 9 - ����ȣ
 * <li> ��� : ���� �ʿ��� ����Ʈ �ڵ带 ��Ÿ������ JDBC ���ҽ��� �����Ѵ�.
 * �� �ڵ�� JDBC ����̹��� �ε��Ǳ� ������ ����Ǿ�� �Ѵ�.
 * </ul>
 *
 * @author ����̻�
 */
public class BootstrapServlet extends HttpServlet {

    static {
        MonitoringManager manager = MonitoringManager.getInstance();
        manager.init();
        manager.getLog().println("BootstrapServlet", "Successfully Loaded");
        manager.getLog().flush();
    }

    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        super.service(req, res);
    }
}
