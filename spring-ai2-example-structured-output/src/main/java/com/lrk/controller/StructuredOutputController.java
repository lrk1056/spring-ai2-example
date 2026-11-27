package com.lrk.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/structured-output")
public class StructuredOutputController {

    @Resource
    private ChatClient openAiChatClient;

    public record TopicBook(
            String topic,  // 主题
            List<String> books // 推荐书籍
    ) {
    }

    @GetMapping(value = "/ask1")
    public String ask1(String topic) {
        TopicBook topicBook = openAiChatClient.prompt() // 创建 Prompt 对象,用于构建聊天请求
                .system("你是一个专业的书评助手")  // 设置系统提示词
                .user(u -> u.text("请给我推荐三本关于{topic}的书籍,只需要返回书名").param("topic", topic))
                .call()
                .entity(TopicBook.class);
        System.out.println(topicBook);
        return "ok";
    }

    public record BookReview(
            String reviewerName,  // 评价人
            int rating, // 评分
            String comment // 评价内容
    ) {
    }

    @GetMapping(value = "/ask2")
    public String ask2() {
        List<BookReview> bookReviews = openAiChatClient.prompt() // 创建 Prompt 对象,用于构建聊天请求
                .user(u -> u.text("请给{bookName}书籍三条评价信息").param("bookName", "深入理解Java虚拟机"))
                .call()
                .entity(new ParameterizedTypeReference<>() {
                });
        System.out.println(bookReviews);
        return "ok";
    }
}
