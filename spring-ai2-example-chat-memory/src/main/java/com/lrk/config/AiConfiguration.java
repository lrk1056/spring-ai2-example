package com.lrk.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
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
                .build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(10) // 设置消息窗口大小为10
                .chatMemoryRepository(new InMemoryChatMemoryRepository()) // 内存存储
                .build();
    }

    /**
     * OpenAI 的聊天模型（带内存记忆）
     */
    @Bean
    public ChatClient openAiChatClientWithMemory(OpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(), // 添加一个日志拦截器
                        MessageChatMemoryAdvisor.builder(chatMemory).build()  // 添加一个聊天记录拦截器
                )
                .build();
    }


}
