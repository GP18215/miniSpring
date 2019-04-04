package com.gupaoedu.spring.mvcframework.v2.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: MiaoYongchang
 * Date: 2019/4/4
 * Time: 13:00
 * Description: No Description
 */
public class GPDispatcherServlet extends HttpServlet {

    //读取properties配置文件信息
    private Properties contextConfig = new Properties();

    //保存扫描到所有的类名
    private List<String> classNames = new ArrayList<String>();

    //ioc容器保存beanName和实例
    private Map<String,Object>  ioc = new HashMap<String, Object>();

    //保存url和Method的对应关系
    private Map<String, Method>  handlerMapping = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {

        //1.加载配置文件
        doLoadConfig(config);

        //2.扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //3.doInstance();初始化扫描到的类并把他们放到Ioc容器中
        doInstance();

        //4.完成依赖注入
        doAutowired();

        //5.初始化HandlerMapping
        initHandlerMapping();

        System.out.println("Mini Spring is init");
    }


    private void initHandlerMapping() {
    }

    private void doAutowired() {
    }

    private void doInstance() {
    }

    private void doScanner(String scanPackage) {
    }


    private void doLoadConfig(ServletConfig config) {
        String contextConfigLocation =
                     config.getInitParameter("contextConfigLocation");
        /**
         * 直接从类路径下找到配置文件,并将其读取到Properties对象中
         * 相当于把文件信息保存到了内存中
         */
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);

        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if(is != null){
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
