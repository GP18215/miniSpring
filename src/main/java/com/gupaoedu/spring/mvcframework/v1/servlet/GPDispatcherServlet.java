package com.gupaoedu.spring.mvcframework.v1.servlet;

import com.gupaoedu.spring.mvcframework.annotatoin.GPAutowired;
import com.gupaoedu.spring.mvcframework.annotatoin.GPController;
import com.gupaoedu.spring.mvcframework.annotatoin.GPRequestMapping;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class GPDispatcherServlet extends HttpServlet {

    private Map<String,Object> mapping = new HashMap<String,Object>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void doDispatch(HttpServletRequest req,HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();

        url = url.replace(contextPath,"").replaceAll("/+","/");

        if(!this.mapping.containsKey(url)){
            resp.getWriter().write("404 not fond!");
            return;
        }

        Method method = (Method) this.mapping.get(url);

        Map<String,String[]> params = req.getParameterMap();

        method.invoke(this.mapping.get(method.getDeclaringClass().getName()),new Object[]{req,resp,params.get("name")[0]});

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        InputStream is = null;
        try{
            Properties configContext = new Properties();
            //得到输入流
            is = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("contextConfigLocation"));
            configContext.load(is);
            String scanPackage = configContext.getProperty("scanPackage");
            doScanner(scanPackage);
            //遍历mapping中所有的key
            for(String className : mapping.keySet()){
                if(!className.contains(".")){continue;}
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(GPController.class)){
                    mapping.put(className,clazz.newInstance());
                    String baseUrl  = "";
                    if(clazz.isAnnotationPresent(GPRequestMapping.class)){
                        GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
                        baseUrl = requestMapping.value();
                    }
                    Method[] methods = clazz.getMethods();
                    for (Method method:methods) {
                        if(!method.isAnnotationPresent(GPRequestMapping.class)){continue;}

                        GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);

                        String url = (baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");

                        mapping.put(url,method);
                        System.out.println("Mapped "+url+","+method);
                    }

                }else if(clazz.isAnnotationPresent(GPService.class)){
                    GPService service = clazz.getAnnotation(GPService.class);

                    String  beanName = service.value();

                    if("".equals(beanName)){
                        beanName=clazz.getName();
                    }
                    Object instance = clazz.newInstance();
                    mapping.put(beanName,instance);

                    for(Class<?> c :clazz.getInterfaces()){//获取该类实现的接口数组
                        mapping.put(c.getName(),instance);
                    }


                }else{
                    continue;
                }

            }

            //注入
            for(Object object:mapping.values()){//遍历mapping中所有的value
                if(object==null){continue;}
                Class<?> clazz = object.getClass();

                if(clazz.isAnnotationPresent(GPController.class)){
                    ////获得某个类的所有声明的字段，即包括public、private和proteced，但是不包括父类的申明字段。
                        Field[] fields = clazz.getDeclaredFields();
                    for (Field field : fields) {

                        if(!field.isAnnotationPresent(GPAutowired.class)){continue;}
                        GPAutowired autowired = field.getAnnotation(GPAutowired.class);
                        String beanName = autowired.value();
                        if("".equals(beanName)){
                            beanName = field.getType().getName();//获取属性对象的类名称
                        }

                        field.setAccessible(true);//强制访问
                        field.set(mapping.get(clazz.getName()),mapping.get(beanName));

                    }

                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(is!=null){
                try{
                    is.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        System.out.println("miniSpring is init OK!");
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader(). getResource("/"+scanPackage.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for(File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String clazzName = scanPackage + "." + file.getName().replace(".class", "");
                mapping.put(clazzName, null);//key是类名
            }
        }
    }
}
