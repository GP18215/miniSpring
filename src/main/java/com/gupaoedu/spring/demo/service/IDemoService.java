package com.gupaoedu.spring.demo.service;

import com.gupaoedu.spring.mvcframework.annotatoin.GPService;

@GPService
public interface IDemoService {

    String get(String name);
}
