package com.lrk.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Usage Handling 文档（https://docs.spring.io/spring-ai/reference/api/usage-handling.html）核心案例演示：
 *
 * AI 调用是要花钱的（按 token 计费），Spring AI 把服务商返回的用量/限流元数据封装成三个类型：
 * ChatResponseMetadata（总入口）、Usage（token 用量）、RateLimit（限流余额）。
 * 1. ChatModel 直调拿完整元数据
 * 2. ChatClient 拿 Usage（文档 "Using with ChatClient"）
 * 3. RateLimit 限流信息 + EmptyRateLimit 判空技巧
 * 4. Prompt 缓存指标（getCacheReadInputTokens / getCacheWriteInputTokens）
 * 5. 多步流程的累计用量（工具调用循环会把每轮模型调用的 token 加总）
 */
@RestController
@RequestMapping("/usage")
public class UsageController {

    @Resource
    private ChatClient openAiChatClient;

    @Resource
    private OpenAiChatModel openAiChatModel; // 底层模型，用于直调拿原始 ChatResponse

    /**
     * 案例 1：ChatModel 直调，拿完整的 ChatResponseMetadata
     *
     * 对应文档 "ChatResponseMetadata" 一节：id / model / rateLimit / usage / promptMetadata 全在这。
     */
    @GetMapping("/metadata")
    public String metadata(String question) {
        ChatResponse response = openAiChatModel.call(new Prompt(question));

        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata.getUsage();

        StringBuilder sb = new StringBuilder();
        sb.append("响应 ID: ").append(metadata.getId()).append("\n");
        sb.append("实际模型: ").append(metadata.getModel()).append("\n");
        sb.append("输入 tokens(prompt): ").append(usage.getPromptTokens()).append("\n");
        sb.append("输出 tokens(completion): ").append(usage.getCompletionTokens()).append("\n");
        sb.append("总 tokens(total): ").append(usage.getTotalTokens()).append("\n");
        System.out.println(sb);
        return sb.toString();
    }

    /**
     * 案例 2：ChatClient 场景下拿 Usage
     *
     * 对应文档 "Using with ChatClient"：关键差异是 .call() 后要调 .chatResponse()
     * 拿完整响应对象，而不是 .content()（content 只剩文本，元数据丢了）。
     */
    @GetMapping("/chat-client-usage")
    public String chatClientUsage(String question) {
        ChatResponse response = openAiChatClient.prompt()
                .user(question)
                .call()
                .chatResponse(); // 注意：不是 content()，要拿完整 ChatResponse 才有元数据

        Usage usage = response.getMetadata().getUsage();
        String answer = response.getResult().getOutput().getText();

        return "回答: " + answer + "\n\n本次消耗 tokens: " + usage.getTotalTokens() + "（输入 " + usage.getPromptTokens() + " / 输出 " + usage.getCompletionTokens() + "）";
    }

    /**
     * 案例 3：RateLimit 限流信息 + EmptyRateLimit 判空
     *
     * 对应文档 "Rate Limit" 一节：限流数据一般来自服务商响应的 HTTP 头。
     * 坑：不支持的服务商返回 EmptyRateLimit（值全是 0），不是 null——
     * 所以不能只判 null，要用 instanceof 区分"没有这数据"和"真的剩 0"。
     */
    @GetMapping("/rate-limit")
    public String rateLimit(String question) {
        ChatResponse response = openAiChatModel.call(new Prompt(question));
        RateLimit rateLimit = response.getMetadata().getRateLimit();

        if (rateLimit instanceof EmptyRateLimit) {
            // DashScope 兼容模式下不透出限流头，会走到这里
            return "当前服务商未返回限流信息（EmptyRateLimit），不是真的剩余额度为 0";
        }
        return "请求余量: " + rateLimit.getRequestsRemaining()
                + "/" + rateLimit.getRequestsLimit()
                + "，token 余量: " + rateLimit.getTokensRemaining()
                + "，重置时间: " + rateLimit.getTokensReset();
    }

    /**
     * 案例 4：Prompt 缓存指标
     *
     * 对应文档 "Prompt Cache Usage Metrics"：支持缓存的服务商（如 Anthropic、OpenAI）
     * 可以看到多少 token 命中了缓存。不支持的服务商这两个方法返回 null。
     * DashScope 兼容模式未透出缓存指标，所以这里大概率是 null——这也正是文档说明的行为。
     */
    @GetMapping("/cache-metrics")
    public String cacheMetrics(String question) {
        ChatResponse response = openAiChatClient.prompt()
                .user(question)
                .call()
                .chatResponse();

        Usage usage = response.getMetadata().getUsage();
        Long cacheRead = usage.getCacheReadInputTokens();
        Long cacheWrite = usage.getCacheWriteInputTokens();

        StringBuilder sb = new StringBuilder();
        sb.append("缓存读 tokens: ").append(cacheRead == null ? "null（服务商不支持或未透出）" : cacheRead).append("\n");
        sb.append("缓存写 tokens: ").append(cacheWrite == null ? "null（服务商不支持或未透出）" : cacheWrite).append("\n");
        sb.append("原生用量对象: ").append(usage.getNativeUsage());
        return sb.toString();
    }

    /**
     * 案例 5：多步流程的累计用量
     *
     * 对应文档 "Cumulative Usage Across Multi-Step Flows"：
     * 工具调用循环会发起多轮模型调用，getUsage() 返回的是每轮加总后的数字，
     * 不是最后一轮的。此时 getNativeUsage() 会返回 null（原生对象没法跨响应合并）。
     * 想看累计效果，可以调 /usage/multi-turn 接口对比单轮的数值。
     */
    @GetMapping("/multi-turn-note")
    public String multiTurnNote() {
        // 单轮：正常拿到 usage 和原生对象
        ChatResponse single = openAiChatClient.prompt().user("用一句话介绍 Redis").call().chatResponse();
        Usage singleUsage = single.getMetadata().getUsage();

        // 多轮对话（同一 prompt 发两次并手动累计，模拟工具循环的加总逻辑）
        ChatResponse first = openAiChatClient.prompt().user("用一句话介绍 MySQL").call().chatResponse();
        ChatResponse second = openAiChatClient.prompt().user("再介绍下 PostgreSQL").call().chatResponse();

        int cumulative = first.getMetadata().getUsage().getTotalTokens() + second.getMetadata().getUsage().getTotalTokens();

        return "单轮 tokens: " + singleUsage.getTotalTokens()
                + "（nativeUsage=" + (singleUsage.getNativeUsage() != null ? "有" : "null") + "）\n"
                + "两轮合计 tokens: " + cumulative + "（在多步流程里这个加总是框架自动做的，见 UsageCalculator）";
    }

}
