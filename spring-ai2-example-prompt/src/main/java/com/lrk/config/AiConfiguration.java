package com.lrk.config;

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
                .defaultAdvisors(new SimpleLoggerAdvisor()) // 内置的日志 Advisor
                .build();
    }

    @Bean
    public ChatClient openAiChatRoleClient(OpenAiChatModel model) {
        // System Prompt 工程：清晰定义 AI 的角色、任务、约束和输出格式
        String systemPrompt = """
                你是一个资深的 Java 技术顾问。
                禁止回答任何非技术类问题，例如天气或娱乐八卦。
                代码示例必须符合 Java 17+ 规范。
                回答需要符合以下格式：首先一句话概括问题的核心，然后提供代码示例，最后补充注意事项。
                如果自己不确定，可以说"关于这个问题，我目前没有确切的信息"，禁止编造内容。
                """;
        return ChatClient
                .builder(model)
                .defaultSystem(systemPrompt)
                .build();
    }

    /**
     * OllamaChatModel 聊天模型
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel model) {
        return ChatClient
                .builder(model)
                .defaultAdvisors(new SimpleLoggerAdvisor()) // 内置的日志 Advisor
                .build();
    }


}
