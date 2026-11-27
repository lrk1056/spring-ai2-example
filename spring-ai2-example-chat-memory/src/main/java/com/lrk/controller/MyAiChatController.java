package com.lrk.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
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
    private ChatClient openAiChatClientWithMemory;

    @GetMapping("/openAiChatDemo")
    public String openAiChatDemo(String question) {
        return openAiChatClient.prompt() // 创建 Prompt 对象,用于构建聊天请求
                .user(question) // 设置用户输入
                .call()  // 调用模型，返回结果
                .content(); // 获取结果内容
    }

    @GetMapping("/openAiChatClientWithMemoryDemo")
    public String openAiChatClientWithMemoryDemo(String question, String convId) {
        return openAiChatClientWithMemory.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .call()
                .content();
    }


}
