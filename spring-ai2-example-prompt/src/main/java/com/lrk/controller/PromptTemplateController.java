package com.lrk.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Prompt 文档（https://docs.spring.io/spring-ai/reference/api/prompt.html）中的核心案例演示：
 * 1. PromptTemplate 基础占位符渲染
 * 2. Role 角色（System + User 多消息组合成 Prompt）
 * 3. 自定义模板分隔符（避免和 JSON 的 {} 冲突）
 * 4. 用 Resource 文件代替硬编码模板字符串
 * 5. 绕过 ChatClient，直接用 ChatModel + Prompt 调用（最底层 API）
 */
@RestController
@RequestMapping("/prompt")
public class PromptTemplateController {

    @Resource
    private ChatClient openAiChatClient;

    @Resource
    private ChatModel openAiChatModel; // 注入的是底层模型，不是封装后的 ChatClient

    /**
     * 案例 1：PromptTemplate 基础用法
     *
     * 文档示例："Tell me a {adjective} joke about {topic}"
     * {name} 就是占位符，render/create 时传入 Map 进行替换。
     */
    @GetMapping("/template-basic")
    public String templateBasic(String adjective, String topic) {
        // 按文档原样造一个讲笑话的模板
        PromptTemplate promptTemplate = new PromptTemplate("讲一个{adjective}的笑话，主题是{topic}");
        Prompt prompt = promptTemplate.create(Map.of("adjective", adjective, "topic", topic));

        // 注意：这里走的是 ChatModel.call(Prompt)，对应文档 "It is common to use the call() method of ChatModel"
        return openAiChatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 案例 2：Role 角色 —— System + User 组合成一条 Prompt
     *
     * 文档示例：SystemMessage 定 persona（人设），UserMessage 提问题，两者合体后一起发给模型。
     * SystemPromptTemplate 专门用来渲染 system 角色的模板。
     */
    @GetMapping("/template-roles")
    public String templateRoles(String topic) {
        // user 消息：用户真实的问题
        String userText = """
                给我讲讲{topic}的核心概念，要求通俗易懂。
                """;
        Message userMessage = new UserMessage(userText.replace("{topic}", topic));

        // system 消息：设定 AI 的人设和回答风格，占位符用 SystemPromptTemplate 渲染
        String systemText = """
                你是一个乐于助人的 AI 助手。
                你的名字是{name}。
                你要用{voice}的风格来回答用户的问题，并在回答的开头报上自己的名字。
                """;
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "小智", "voice", "单口相声演员"));

        // 多条消息按角色组合成一条 Prompt
        Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
        return openAiChatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 案例 3：Assistant 消息 —— 把历史回答塞回上下文（少样本示例）
     * 文档对 Assistant 角色的解释：代表 AI 之前的回答，跟踪它可以保证对话连贯。
     * 实战里最常用的姿势：给模型看一问一答的示例，让它模仿着回答。
     */
    @GetMapping("/template-assistant")
    public String templateAssistant(String bookName) {
        Message systemMessage = new SystemMessage("你是一个书评助手。严格模仿示例中的评价风格和字数来回答。");
        Message userExample = new UserMessage("书籍：《三体》");
        Message assistantExample = new AssistantMessage("《三体》：硬科幻巅峰之作，宇宙社会学设定震撼，强烈推荐。");
        Message userQuestion = new UserMessage("书籍：" + bookName);

        // System -> 问答示例对 -> 真实问题，模型会模仿示例的风格输出
        Prompt prompt = new Prompt(List.of(systemMessage, userExample, assistantExample, userQuestion));
        return openAiChatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 案例 4：自定义模板分隔符
     * 文档原话：如果 prompt 里要放 JSON，默认的 {} 会和 JSON 语法打架，
     * 换成 < > 当分隔符就能共存了。
     */
    @GetMapping("/template-delimiter")
    public String templateDelimiter() {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                // 用 < > 替代默认的 { } 作为占位符分隔符
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template("""
                        请严格按以下 JSON 格式输出推荐结果，不要输出多余内容：
                        {"books": [{"title": "<title>", "reason": "<reason>"}]}
                        请推荐一本关于<subject>的书籍并填充上述 JSON。
                        """)
                .build();

        // 渲染时 <title>/<reason>/<subject> 被替换，JSON 的 {} 原样保留
        Prompt prompt = promptTemplate.create(Map.of(
                "title", "由你推荐",
                "reason", "一句话理由",
                "subject", "并发编程"));
        return openAiChatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 案例 5：用 Resource 文件代替硬编码模板字符串
     * 文档示例：@Value("classpath:/prompts/system-message.st")
     * 好处：模板改动不用重新编译 Java 代码，也方便集中管理 prompt 文件。
     */
    @Value("classpath:/prompts/it-consultant-system-message.st")
    private org.springframework.core.io.Resource systemResource;

    @GetMapping("/template-resource")
    public String templateResource(String role, String tone, String question) {
        // SystemPromptTemplate 支持直接传 Resource 对象
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("role", role, "tone", tone));
        Prompt prompt = new Prompt(List.of(systemMessage, new UserMessage(question)));
        return openAiChatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 案例 6：render() 纯渲染（不调用模型）
     * 只想看看占位符替换后的最终字符串时用 render()，
     * 调试模板时非常有用，不消耗 token。
     */
    @GetMapping("/template-render-only")
    public String templateRenderOnly(String adjective, String topic) {
        PromptTemplate promptTemplate = new PromptTemplate("讲一个{adjective}的笑话，主题是{topic}");
        // render 只做字符串替换，不发起模型调用
        String rendered = promptTemplate.render(Map.of("adjective", adjective, "topic", topic));
        return "渲染后的 Prompt：" + rendered;
    }

}
