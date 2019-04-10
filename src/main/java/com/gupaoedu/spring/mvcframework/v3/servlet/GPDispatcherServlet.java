package com.gupaoedu.spring.mvcframework.v3.servlet;

import com.gupaoedu.spring.mvcframework.annotatoin.*;
import sun.reflect.generics.scope.MethodScope;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: MiaoYongchang
 * Date: 2019/4/4
 * Time: 13:00
 * Description: No Description
 */
public class GPDispatcherServlet extends HttpServlet {

    //保存所有URL和方法的映射关系
    List<Handler> handlerMapping = new ArrayList<Handler>();

    //读取properties配置文件信息
    private Properties contextConfig = new Properties();

    //保存扫描到所有的类名
    private List<String> classNames = new ArrayList<String>();

    //ioc容器保存beanName和实例
    private Map<String,Object>  ioc = new HashMap<String, Object>();

    //保存url和Method的对应关系
    //private Map<String, Method>  handlerMapping = new HashMap<String, Method>();

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
        Handler handler = getHandler(req);
        if(handler == null){
            resp.getWriter().write("404 not fond!");
            return;
        }
        Class<?>[] paramTypes = handler.getParamTypes();
        Object[] paramValues = new Object[paramTypes.length];
        Map<String,String[]> params = req.getParameterMap();

        for (Map.Entry<String, String[]> param : params.entrySet()) {
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]","")
                    .replaceAll("\\s",",");
            int index = handler.paramIndexMapping.get(param.getKey());
            paramValues[index] = convert(paramTypes[index],value);

        }

        if(handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())){
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }

        if(handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())){
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
        }

        Object returnValue = handler.method.invoke(handler.controller,paramValues);
        if(returnValue == null || returnValue instanceof  Void){return;};
        resp.getWriter().write(returnValue.toString());




    }

    private Handler getHandler(HttpServletRequest req){
        if(handlerMapping.isEmpty()){ return null;}
        String url = req.getRequestURI();

        String contextPath = req.getContextPath();

        url = url.replaceAll("contextPath","").replaceAll("/+","/");
        if(url.lastIndexOf(".")>-1){
            url = url.substring(0,url.lastIndexOf("."));
        }

        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if(!matcher.matches()){
                continue;
            }
            return  handler;
        }
        return null;
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
                String regex = ("/"+baseUrl+requestMapping.value()).replaceAll("/+","/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern,entry.getValue(),method));
                System.out.println("Mapped:"+regex+","+method);

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

                className = clazz.getSimpleName();

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

    /**
     * Hander记录Controller中的RequestMapping和Method的对应关系
     */
    private class Handler {

        protected Object controller;//保存方法对应的实例
        protected Method method;   //保存映射的方法
        protected Pattern pattern;
        protected Map<String,Integer> paramIndexMapping;//参数顺序
        private Class<?> [] paramTypes;

        public Handler(Pattern pattern,Object controller,Method method){
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            this.paramIndexMapping = new HashMap<String, Integer>();
            this.paramTypes = method.getParameterTypes();
            putParamIndexMapping(method);
        }

        public Pattern getPattern() {
            return pattern;
        }

        public Method getMethod() {
            return method;
        }

        public Object getController() {
            return controller;
        }

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }

        private void putParamIndexMapping(Method method) {
            Annotation[][] pa = method.getParameterAnnotations();//取出方法参数中的注解

            for(int i=0;i<pa.length;i++){
                for(Annotation a:pa[i]){
                    if(a instanceof GPRequestParam){

                        String paramName = ((GPRequestParam) a).value();
                        if(!"".equals(paramName.trim())){//注解中的值不等于空
                            paramIndexMapping.put(paramName,i);

                        }

                    }

                }
            }
            //提取方法中的request和response的参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for(int i=0;i < paramsTypes.length;i++){
                Class<?> type = paramsTypes[i];
                if(type == HttpServletRequest.class || type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }

            }
        }




    }
}
