package com.lrk.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 实现CallAdvisor和StreamAdvisor接口，同时支持两种模式
 */
public class MySimpleLoggerAdvisor implements CallAdvisor, StreamAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        System.out.println("发送请求前: " + request);
        ChatClientResponse response = chain.nextCall(request);
        System.out.println("接收到响应: " + response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        System.out.println("发送流式请求：" + request);
        return chain.nextStream(request)
                .doOnNext(response -> System.out.println("收到流式响应片段：" + response));
    }

    @Override
    public String getName() {
        return "简单日志Advisor";
    }

    /**
     * 这个 getOrder() 方法用于指定 Advisor（通知器）的执行顺序。
     * 作用说明：
     * 返回值越小，优先级越高，越早执行
     * 返回 0 表示高优先级
     * 如果有多个 Advisor，Spring AI 会按照此值从小到大依次执行
     *
     * @return
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
