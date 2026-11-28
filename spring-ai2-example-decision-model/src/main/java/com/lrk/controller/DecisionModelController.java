package com.lrk.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decision Models 文档（https://docs.spring.io/spring-ai/reference/api/decision.html）核心案例演示。
 * <p>
 * 决策模型不是聊天模型：它不从"选项外的答案"里自由发挥，而是从你给的候选项里挑一个，
 * 并报告每个选项的概率。适合分类、守门（guardrail）、简单业务判断这类"选项有限的决策"。
 * <p>
 * 输入与问题均为中文，便于理解（文档原版示例为英文，语义一致）。
 */
@RestController
@RequestMapping("/decision")
public class DecisionModelController {

    @Resource
    private DecisionModel decisionModel; // OllamaDecisionAutoConfiguration 自动注册

    /**
     * 案例 1：三种问题一次问完（文档 Quick Start 的客服消息分诊示例）
     *
     * YesNoQuestion 判断是否、ChoiceQuestion 分类、ScoreQuestion 打分。
     * 一次调用问多个问题，比多次调用聊天模型更快更省。
     */
    @GetMapping("/triage")
    public String triage() {
        DecisionResponse response = decisionModel.call(DecisionPrompt.builder()
                // 输入：一条客户留言（文档原版：新买的耳机左耳没声音，想退款）
                .text("我昨天刚收到新买的耳机，但是左耳完全没有声音，可以退款吗？")
                // 是非题：whenYes/whenNo 描述"什么算 Yes/No"，帮模型对齐判断标准
                .question("defect", YesNoQuestion.builder()
                        .text("客户是否反映商品存在质量缺陷？")
                        .whenYes("商品的一部分无法正常使用")
                        .whenNo("商品功能正常，消息是关于其他事情的")
                        .build())
                // 选择题：每个选项可以带描述，消除歧义（如 product 专指"收到的商品有问题"）
                .question("topic", ChoiceQuestion.builder()
                        .text("这条消息主要关于什么？")
                        .option("delivery", "订单在哪里，或者什么时候送达")
                        .option("product", "客户收到的商品存在问题")
                        .option("account", "登录、地址或支付信息相关")
                        .option("other", "以上都不是")
                        .build())
                // 打分题：三个级别的量表
                .question("satisfaction", ScoreQuestion.of("客户的满意度如何？", "不满意", "一般", "满意"))
                .build());

        boolean defect = response.getYesNo("defect").isYes();
        String topic = response.getChoice("topic").getValue();
        int satisfaction = response.getScore("satisfaction").getNearestLevel();

        return "是否质量缺陷: " + (defect ? "是" : "否")
                + "\n消息主题: " + topic
                + "\n满意度级别: " + satisfaction + "（0=不满意, 1=一般, 2=满意）";
    }

    /**
     * 案例 2：概率与阈值 —— isYes(0.9)
     *
     * 对应文档 "Yes/No Answers"：isYes() 默认阈值 0.5，
     * 代价高的动作（如自动开维修单）应该提高阈值，让模型"非常确定"才动手。
     */
    @GetMapping("/threshold")
    public String threshold() {
        DecisionResponse response = decisionModel.call(
                "我耳机用了两天左耳就不响了。",
                Map.of("defect", YesNoQuestion.of("客户是否反映商品存在质量缺陷？")));

        double p = response.getYesNo("defect").getProbability();
        boolean autoTicket = response.getYesNo("defect").isYes(0.9); // 高阈值：90% 确定才自动开单
        boolean defaultYes = response.getYesNo("defect").isYes();    // 默认阈值 0.5

        return "Yes 概率: " + String.format("%.3f", p)
                + "\n默认阈值 0.5 判定: " + (defaultYes ? "Yes" : "No")
                + "\n高阈值 0.9 判定（自动开维修单）: " + (autoTicket ? "开单" : "转人工");
    }

    /**
     * 案例 3：选择题的概率分布 —— getRankedOptions()
     *
     * 对应文档 "Choice Answers"：拿到每个选项的概率排行，
     * 第一名和第二名差距太小说明"消息本身不清晰"，适合转人工。
     */
    @GetMapping("/choice-rank")
    public String choiceRank() {
        DecisionResponse response = decisionModel.call(
                "我的订单到哪了？都下单两周了还没到。",
                Map.of("topic", ChoiceQuestion.of("这条消息主要关于什么？", "delivery", "product", "account", "other")));

        var answer = response.getChoice("topic");
        StringBuilder sb = new StringBuilder();
        sb.append("选中: ").append(answer.getValue());
        Double prob = answer.getProbability();
        if (prob != null) {
            sb.append("（概率 ").append(String.format("%.3f", prob)).append("）");
        }
        List<String> ranked = answer.getRankedOptions();
        if (ranked != null && ranked.size() >= 2) {
            sb.append("\n概率排行: ").append(ranked);
        }
        return sb.toString();
    }

    /**
     * 案例 4：结构化字段输入 —— DecisionInput.fields()
     *
     * 对应文档 "Inputs"：输入不止纯文本，还能带命名字段（模型知道每个值的含义）。
     * 用 LinkedHashMap 保持字段顺序。
     */
    @GetMapping("/fields-input")
    public String fieldsInput() {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("message", "这已经是我第三次遇到订单迟到了。");
        customer.put("orders", 12);
        customer.put("late_deliveries", 3);

        DecisionResponse response = decisionModel.call(
                DecisionInput.fields(customer),
                Map.of("repeated", YesNoQuestion.of("客户是否表示这个问题以前也发生过？")));

        return "是否历史重复问题: " + (response.getYesNo("repeated").isYes() ? "是" : "否")
                + "（Yes 概率 " + String.format("%.3f", response.getYesNo("repeated").getProbability()) + "）";
    }

    /**
     * 案例 5：对话输入 —— DecisionInput.conversation() 做守门（guardrail）
     *
     * 对应文档 "Inputs" + 开篇 guardrail 场景：检查"最后一条助手回复是否真的回答了用户的问题"，
     * 这是 RAG 场景里防止"答非所问"的典型用法。
     */
    @GetMapping("/guardrail")
    public String guardrail() {
        DecisionResponse response = decisionModel.call(
                DecisionInput.conversation(List.of(
                        new UserMessage("耳机可以退货吗？"),
                        new AssistantMessage("您的订单将于本周五送达。"))),
                Map.of("answered", YesNoQuestion.of("最后一条助手回复是否回答了用户的问题？")));

        boolean answered = response.getYesNo("answered").isYes();
        return "助手是否回答了问题: " + (answered ? "是（放行）" : "否（拦截，文档预期: 否，因为助手答的是到货时间而非退货）");
    }

    /**
     * 案例 6：ScoreAnswer 的三种读法 —— getNearestLevel / getValue / getRoundedLevel
     *
     * 对应文档 "Score Answers"：期望值(getValue)可能落在模型认为概率为零的级别上，
     * 三种读法结果可以完全不同——文档原版的反直觉例子。
     */
    @GetMapping("/score-reading")
    public String scoreReading() {
        DecisionResponse response = decisionModel.call(
                "这个产品凑合吧，没什么特别的，但能用。",
                Map.of("satisfaction", ScoreQuestion.of("客户的满意度如何？", "不满意", "一般", "满意")));

        ScoreAnswer answer = response.getScore("satisfaction");
        StringBuilder sb = new StringBuilder();
        sb.append("最可能级别 nearestLevel: ").append(answer.getNearestLevel());
        sb.append("\n加权期望值 getValue: ").append(String.format("%.2f", answer.getValue()));
        sb.append("\n期望值取整 roundedLevel: ").append(answer.getRoundedLevel());
        List<Double> probs = answer.getProbabilities();
        if (probs != null) {
            sb.append("\n各级别概率: ").append(probs).append("（对应 不满意/一般/满意）");
        }
        return sb.toString();
    }

    /**
     * 案例 7：调用元数据 —— token 用量
     *
     * 对应文档 "Metadata"：决策调用也消耗 token，从 response.getMetadata() 拿用量。
     */
    @GetMapping("/metadata")
    public String metadata() {
        DecisionResponse response = decisionModel.call(
                "我要申请退款，收到时外箱都压瘪了。",
                Map.of("topic", ChoiceQuestion.of("这条消息主要关于什么？", "delivery", "product", "account", "other")));

        var meta = response.getMetadata();
        var usage = meta.getUsage();
        return "主题: " + response.getChoice("topic").getValue()
                + "\ntoken 用量: 输入 " + usage.getPromptTokens()
                + " / 输出 " + usage.getCompletionTokens()
                + " / 合计 " + usage.getTotalTokens();
    }

}
