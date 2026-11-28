package com.lrk.controller;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static java.util.stream.Collectors.toMap;

/**
 * 演示：接入外部 MCP 服务（12306 火车票查询），由大模型按需多次调用远端工具完成复杂任务。
 * <p>
 * 12306-mcp 通过 <b>stdio</b> 传输接入（见 application.yaml 中 spring.ai.mcp.client.stdio.connections），
 * 客户端启动时会自动执行 {@code npx -y 12306-mcp} 拉起子进程，无需手动启动服务。
 * 本地自建的天气 mcp-server 则通过 <b>streamable-http</b> 传输接入，两种方式互为对照。
 * <p>
 * 典型的多步工具调用流程（以"查询明天下午 12 点之后北京到郑州的高铁"为例）：
 * <ol>
 *     <li>模型先调用 {@code get-current-date} 获取今天日期，推算出"明天"的具体日期</li>
 *     <li>再调用 {@code get-station-code-of-citys} 将"北京/郑州"转换为 12306 的车站编码</li>
 *     <li>最后调用 {@code get-tickets}（携带日期、起止站、trainFilterFlags=G、earliestStartTime=12）完成查询</li>
 * </ol>
 */
@RestController
@RequestMapping("/12306")
public class My12306Controller {

    @Autowired
    private ChatClient openAiChatClient;

    /**
     * 与所有已连接 MCP 服务端的同步客户端（Spring AI 根据 yaml 配置自动创建）
     */
    @Autowired
    private List<McpSyncClient> mcpSyncClients;

    /**
     * 查询明天下午 12 点之后北京到郑州的高铁班次
     * <p>
     * 无需在代码里写死任何步骤，把 12306 的全部工具交给模型，
     * 由模型根据用户问题自主规划：先解析相对日期 -> 再查车站编码 -> 最后查余票并筛选 G 字头车次。
     */
    @GetMapping("/tickets")
    public String tickets() {
        String question = "查询明天下午12点之后北京到郑州的高铁有哪些班次？请按出发时间列出车次、出发/到达时间、历时和二等座余票情况。";
        String result = openAiChatClient.prompt()
                .system("用户询问火车票时，你必须先调用工具获取当前日期和车站编码，再查询余票，并用简洁的表格（Markdown）形式用中文回答。")
                .user(question)
                .tools(build12306ToolCallbackProvider()) // 只注册 12306 的工具
                .call()
                .content();
        System.out.println(result);
        return result;
    }

    /**
     * 构建只包含 12306-mcp 工具的回调提供器
     * <p>
     * 通过 {@link McpToolFilter} 按"连接名"过滤：yaml 中 stdio.connections 的 key（12306-mcp）
     * 会成为连接名，最终体现在客户端信息 {@code clientInfo.name()} 中（格式为 "客户端名 - 连接名"）。
     * 这样即使同时连接了本地天气服务与外部 12306 服务，也不会把天气工具误传给本场景的模型。
     */
    private SyncMcpToolCallbackProvider build12306ToolCallbackProvider() {
        // 过滤条件：仅保留连接名以 "12306-mcp" 结尾的 MCP 服务端的工具
        BiPredicate<McpConnectionInfo, McpSchema.Tool> only12306 = (mcpConnInfo, tool) -> mcpConnInfo.clientInfo().name().endsWith("12306-mcp");

        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClients)
                .toolFilter(only12306::test)
                .build();
    }

    /**
     * 调试用：按 MCP 连接名分组，列出当前客户端已注册的全部外部工具
     */
    @GetMapping("/debug/tools")
    public Map<String, List<String>> debugTools() {
        return mcpSyncClients.stream()
                .collect(toMap(
                        client -> client.getClientInfo().name(), // 形如 "spring-ai2-example-mcp-client - 12306-mcp"
                        client -> client.listTools().tools().stream()
                                .map(t -> t.name() + ": " + t.description())
                                .toList()
                ));
    }

}
