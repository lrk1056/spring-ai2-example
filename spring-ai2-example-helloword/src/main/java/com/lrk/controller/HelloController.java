package com.lrk.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    /**
     * 简单的 hello 入口，用于验证服务可正常启动与访问
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello, Spring AI!";
    }
}
