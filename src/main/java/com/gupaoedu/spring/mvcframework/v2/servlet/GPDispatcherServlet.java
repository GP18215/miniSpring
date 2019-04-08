package com.gupaoedu.spring.mvcframework.v2.servlet;

import com.gupaoedu.spring.mvcframework.annotatoin.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
        try {
            doDispatch(req,resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        //绝对路径
        String url = req.getRequestURI();

        //处理成相对路径
        String contextPath = req.getContextPath();

        url = url.replaceAll(contextPath,"").replaceAll("/+","/");

        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404");
            return;
        }

        Method method = this.handlerMapping.get(url);

        //从request拿到url传过来的参数

        Map<String,String[]> params = req.getParameterMap();

        //获取方法的形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();

        Object[] paramValues = new Object[parameterTypes.length];

        for(int i=0;i<parameterTypes.length;i++){
            Class parameterType = parameterTypes[i];
            //不能用instanceof,parameterType是形参不是实参
            if(parameterType == HttpServletRequest.class){
                paramValues[i] = req;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = resp;
                continue;
            }else if(parameterType == String.class){
                GPRequestParam requestParam = (GPRequestParam) parameterType.getAnnotation(GPRequestParam.class);
                String paramV = requestParam.value();
                if(params.containsKey(paramV)){
                    for (Map.Entry<String, String[]> param : params.entrySet()) {
                        String value = Arrays.toString(param.getValue());
                        value = value.replaceAll("\\[|\\]","");
                        value = value.replaceAll("\\s",",");
                        paramValues[i] = value;

                    }

                }
            }

        }

        //通过反射拿到method所在class,拿到class之后再拿到class的className
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),paramValues);
    }

    //url传过来的参数都是String类型的，HTTP是基于字符串协议
    //把字符串转换成其他想要的类型
    private Object convert(Class<?> type,String value){
        //如果是int
        if(Integer.class == type){
            return Integer.valueOf(value);
        }

        return value;

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


    /**
     * 保存url和method之间的映射关系
     */
    private void initHandlerMapping() {
        if(ioc.isEmpty()){return;};

        for(Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(GPController.class)){continue;}

            //保存写在类上面的GPRequestMapping("/demo")
            String baseUrl = "";
            if(clazz.isAnnotationPresent(GPRequestMapping.class)){
                GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //获取所有的public方法
            for(Method method : clazz.getMethods()){

                if(!method.isAnnotationPresent(GPRequestMapping.class)){continue;}

                GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);
                // /demo/query
                String url = ("/"+baseUrl+"/"+requestMapping.value()).replaceAll("/+","/");

                handlerMapping.put(url,method);

                System.out.println("Mapped:"+url+","+method);

            }




        }
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
                doScanner(scanPackage + "." + file.getName());
            }else{
                if(!file.getName().endsWith(".class")){//判断该文件是不是.class结尾
                    continue;
                }
                //完整路径拼接出来并放入list集合中
                String className = scanPackage+"."+file.getName().replace(".class","");
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
