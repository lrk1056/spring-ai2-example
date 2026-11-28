package com.lrk.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

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

    // Flux 流式输出
    @GetMapping(value = "/openAiChatStreamResult", produces = "text/html;charset=utf-8")
    public Flux<String> openAiChatStreamResult(String question) {
        return openAiChatClient.prompt()
                .user(question)
                .stream() // 流式响应输出
                .content();
    }

}
