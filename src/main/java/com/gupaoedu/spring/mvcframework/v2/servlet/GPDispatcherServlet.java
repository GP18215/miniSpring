package com.gupaoedu.spring.mvcframework.v2.servlet;

import com.gupaoedu.spring.mvcframework.annotatoin.GPAutowired;
import com.gupaoedu.spring.mvcframework.annotatoin.GPController;
import com.gupaoedu.spring.mvcframework.annotatoin.GPService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
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

    /**
     *自动依赖注入
     */
    private void doAutowired() {
        if(ioc.isEmpty()){
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {

            //getDeclaredFields拿到所有的字段包括private/public/protected/defualt
            Field[] fields = entry.getValue().getClass().getDeclaredFields();

            for(Field field:fields){
                if(!field.isAnnotationPresent(GPAutowired.class)){ continue;}
                GPAutowired autowired = field.getAnnotation(GPAutowired.class);

                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }

                field.setAccessible(true);//强制访问
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * 初始化实例bean,并放在ioc容器中
     */
    private void doInstance() {
      //初始化为DI做准备
        if(classNames.isEmpty()){return;}
        try{
            for (String className:classNames){
                Class<?> clazz = Class.forName(className);

                if(clazz.isAnnotationPresent(GPController.class)){//判断类是否用了GPController注解
                      Object instance =  clazz.newInstance();
                      //Spring默认类名首字母小写
                      String beanName = toLowerFirstCase(className);

                      ioc.put(beanName,instance);

                }else if(clazz.isAnnotationPresent(GPService.class)){//判断类是否用了GPService注解
                    //1.可能是自定义的beanName
                    GPService service = clazz.getAnnotation(GPService.class);
                    String beanName = service.value();
                    //2.若为空则用默认的beanName,类名首字母小写
                    if("".equals(beanName)){
                        beanName = toLowerFirstCase(className);
                    }

                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);

                    //3.根据类型自动复制
                    for(Class<?> i : clazz.getInterfaces()){
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("The "+i.getName()+" is Exists!");
                        }
                        //把接口类型直接当成了Key
                        ioc.put(i.getName(),instance);
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private String toLowerFirstCase(String className) {
        char[] chars = className.toCharArray();
        //取首字母将其ASCII码＋32就是小写字母
        chars[0] += 32;

        return String.valueOf(chars);
    }

    /**
     * 扫描配置的包下所有的类,并将className存入集合中
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        //scanPackage = com.gupaoedu.spring.demo
        //转换成文件路径，实际上就是把.转换成\
        //classpath
        URL url = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));

        File classpath = new File(url.getFile());
        for(File file : classpath.listFiles()){//获取路径下的所有的文件
            if(file.isDirectory()){//如果该文件是文件夹递归调用
                doScanner(file.getName());
            }else{
                if(!file.getName().endsWith(".class")){//判断该文件是不是.class结尾
                    continue;
                }
                //完整路径拼接出来并放入list集合中
                String className = scanPackage+"."+file.getName().replace(".calss","");
                classNames.add(className);
            }

        }

    }


    /**
     * 加载配置信息
     * @param config
     */
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
