package com.lrk.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 演示：通过 Spring AI MCP 客户端连接本机 {@code mcp-server}，由大模型按需调用远端 MCP 工具（如天气预报）。
 * <p>
 * 使用前请先启动 {@code mcp-server}（默认端口 8888）。
 */
@RestController
public class MyMcpClientController {

    @Autowired
    private ChatClient openAiChatClient;

    @Autowired
    private SyncMcpToolCallbackProvider mcpToolCallbackProvider;


    /**
     * 调试用：查看当前客户端已注册的 MCP 工具列表
     */
    @GetMapping("/debug/mcp-tools")
    public List<String> tools() {
        List<String> tools = Arrays.stream(mcpToolCallbackProvider.getToolCallbacks())
                .map(t -> t.getToolDefinition().name())
                .toList();
        return tools;
    }

    /**
     * 使用 MCP 工具
     */
    @RequestMapping("/ai/mcp")
    public String aiWithMcp() {
        String question = "查一下上海天气";
        String result = openAiChatClient.prompt()
                .system("用户询问天气时，你必须调用工具查询后再用简短中文回答。")
                .user(question)
                .tools(mcpToolCallbackProvider)
                .call()
                .content();
        System.out.println(result);
        return "OK";
    }

}
