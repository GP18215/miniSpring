package com.gupaoedu.spring.mvcframework.v1.servlet;

import com.gupaoedu.spring.mvcframework.annotatoin.GPController;
import com.gupaoedu.spring.mvcframework.annotatoin.GPRequestMapping;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class GPDispatcherServlet extends HttpServlet {

    private Map<String,Object> mapping = new HashMap<String,Object>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        InputStream is = null;
        try{
            Properties configContext = new Properties();
            //得到输入流
            is = this.getClass().getClassLoader().getResourceAsStream("contextConfigLocation");
            configContext.load(is);
            String scanPackage = configContext.getProperty("scanPackage");
            doScanner(scanPackage);
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

                        String url = (baseUrl + "/" + requestMapping.value()).replaceAll("/+",",");

                        mapping.put(url,method);


                    }






                }

            }


        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader(). getResource("/"+scanPackage.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for(File file : classDir.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage+"."+file.getName());
            }else if(!file.getName().endsWith(".class")){
                continue;
            }
            String clazzName = scanPackage + "." +file.getName().replace(".class","");
            mapping.put(clazzName,null);//key是类名
        }

    }
}
