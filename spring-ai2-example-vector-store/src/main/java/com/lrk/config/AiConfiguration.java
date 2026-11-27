package com.lrk.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiConfiguration {

    /**
     * OpenAI 的聊天模型
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel model) {
        return ChatClient
                .builder(model)
//                .defaultAdvisors(new SimpleLoggerAdvisor()) // 内置的日志 Advisor
                .build();
    }

//    @Bean
//    List<RedisVectorStore.MetadataField> redisMetadataFields() {
//        return List.of(RedisVectorStore.MetadataField.tag("conversationId"));
//    }

}
