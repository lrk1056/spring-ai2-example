# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Spring AI 2 特性示例集合，每个特性对应一个**独立 Maven 项目**（无聚合 pom，根目录没有父 pom.xml）。技术栈：Spring AI 2.1.0-M1、Spring Boot 4.2.0、JDK 21。

对话与回答使用简体中文，代码注释也使用中文。

## 构建与运行

必须在各模块目录内执行（模块自带 mvnw wrapper）：

```bash
cd spring-ai2-example-advisor
./mvnw compile            # 编译
./mvnw test               # 测试（单测多为空壳上下文加载测试，非断言型）
./mvnw test -Dtest=MyAiChatControllerTest   # 运行单个测试
./mvnw spring-boot:run    # 启动应用
```

模块间完全独立：修改某模块后只需在该模块内编译/运行，不要在仓库根目录找 mvnw 或 pom.xml。

## 关键架构模式

- **每个模块 = 一个特性演示**：advisor / chat-memory / embedding / prompt / simple-rag / structured-output / tools / vector-store / multimodality，mcp 模块下再嵌套两个独立子项目 `spring-ai2-example-mcp-server`（端口 8080）和 `spring-ai2-example-mcp-client`（端口 8081）。
- **统一代码结构**：`config/AiConfiguration.java` 定义 ChatClient Bean（按模型命名，如 `openAiChatClient`、`ollamaChatClient`），`controller/` 暴露演示接口。新特性模块遵循同样布局。
- **模型接入**：全部走 OpenAI 兼容协议——云端为阿里云百炼 DashScope（`base-url: https://dashscope.aliyuncs.com/compatible-mode/v1`，模型 `qwen-plus`），本地为 Ollama（`http://localhost:11434`，模型 `qwen3-vl:4b`）；embedding 用 `text-embedding-v4`。
- **API Key**：通过环境变量 `AI_DASHSCOPE_API_KEY` 注入（yaml 中 `${AI_DASHSCOPE_API_KEY}`），运行前必须设置，不要把真实 key 写入 yaml。

## 模块运行依赖

- **vector-store / simple-rag**：需要本地 Redis（vector store 用 Redis 实现）。
- **simple-rag**：启动后先调 `POST /addDocs` 加载 `classpath:docs` 下的文档入库，才能测试 `/ask_rag`。
- **mcp-client**：连接 mcp-server（streamable-http）和外部 12306-mcp（stdio，自动 `npx -y 12306-mcp` 拉起，需 Node.js）。已配置 `spring.ai.mcp.client.initialized=false`（惰性连接），mcp-server 不在线也能启动，首次调用工具时才握手；但调用时服务端必须在线。12306 场景的 `request-timeout` 设为 120s（首次 npx 需下载依赖）。

## 已知坑

- mcp 模块拆分前的旧编译产物残留在 `target/classes` 时，`spring-boot:run` 会报 "Unable to find a single main class"——删除 `target/classes` 中多余的 `*Application.class` 或 `./mvnw clean` 即可。
- 各模块 `server.port` 不同模块可能相同（多为默认 8080，mcp-client 为 8081），同时运行多个模块注意端口冲突。
- MCP 客户端按"连接名"区分多个 MCP 服务端（`connectionInfo.clientInfo().name()` 以连接名结尾），需要按服务端过滤工具时用 `McpToolFilter` + 独立构建 `SyncMcpToolCallbackProvider`，而不是依赖全局自动配置的 `mcpToolCallbacks` Bean（它是全连接聚合，且其 Filter 是全局生效的）。
