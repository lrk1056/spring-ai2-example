package com.lrk.config;

import com.lrk.advisor.MySimpleLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
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
                .builder(model)
//                .defaultAdvisors(new MySimpleLoggerAdvisor()) // 添加一个 MySimpleLoggerAdvisor，记录请求日志
                .defaultAdvisors(new SimpleLoggerAdvisor()) // 内置的日志 Advisor
                .build();
    }

    /**
     * OllamaChatModel 聊天模型
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel model) {
        return ChatClient
                .builder(model)
                .defaultAdvisors(new MySimpleLoggerAdvisor()) // 添加一个 MySimpleLoggerAdvisor，记录请求日志
                .build();
    }

}
