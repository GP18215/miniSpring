package com.gupaoedu.spring.mvcframework.annotatoin;

import java.lang.annotation.*;

/**
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented

public @interface GPService {

    String value() default "";
}
