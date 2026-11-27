package com.lrk.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class MyMultimodalityController {

    @Autowired
    private ChatClient openAiChatClient;

    // Flux 流式输出
    @GetMapping(value = "/analyze-image", produces = "text/html;charset=utf-8")
    public Flux<String> analyzeImage() {
        // 流畅式API构建多模态请求
        return openAiChatClient.prompt()
                .user(u -> u
                        .text("请详细描述这张图片的内容，包括物体、颜色、场景和可能的用途")
                        .media(MediaType.IMAGE_PNG, new ClassPathResource("docs/img.png"))
                )
                .stream() // 流式响应输出
                .content();
    }
}
