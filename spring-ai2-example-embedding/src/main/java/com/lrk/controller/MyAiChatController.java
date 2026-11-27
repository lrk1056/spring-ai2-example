package com.lrk.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyAiChatController {

    @Resource
    private ChatClient openAiChatClient;

    @GetMapping("/openAiChatDemo")
    public String openAiChatDemo(String question) {
        return openAiChatClient.prompt() // 创建 Prompt 对象,用于构建聊天请求
                .user(question) // 设置用户输入
                .call()  // 调用模型，返回结果
                .content(); // 获取结果内容
    }

}
