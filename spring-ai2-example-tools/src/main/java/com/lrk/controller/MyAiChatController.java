package com.lrk.controller;

import com.lrk.config.tool.DateTimeTools;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
public class MyAiChatController {

    @Resource
    private ChatClient openAiChatClient;
    @Resource
    private DateTimeTools dateTimeTools; // 假设 DateTimeTools 是一个工具类

    @GetMapping("/openAiChatDemo")
    public String openAiChatDemo(String question) {
        return openAiChatClient.prompt() // 创建 Prompt 对象,用于构建聊天请求
                .user(question) // 设置用户输入
                .call()  // 调用模型，返回结果
                .content(); // 获取结果内容
    }

    @GetMapping("/openAiChatWithToolsDemo")
    public String openAiChatWithToolsDemo(String question) {
        return openAiChatClient.prompt()
                .user(question)
                .tools(dateTimeTools)       // 调用模型
                .call()
                .content();
    }


}
