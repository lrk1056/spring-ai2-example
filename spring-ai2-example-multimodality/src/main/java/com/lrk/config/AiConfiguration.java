package com.lrk.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {

    /**
     * OpenAI 的聊天模型
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel model) {
        return ChatClient
                .builder(model) // 创建 ChatClient 对象，以及设置模型为model
                .build();       // 构建 ChatClient 对象
    }

}
