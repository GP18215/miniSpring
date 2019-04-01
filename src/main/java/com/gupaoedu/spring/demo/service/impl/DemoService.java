package com.gupaoedu.spring.demo.service.impl;

import com.gupaoedu.spring.demo.service.IDemoService;

public class DemoService implements IDemoService {
    @Override
    public String get(String name) {
        return "My name is "+name;
    }
}
