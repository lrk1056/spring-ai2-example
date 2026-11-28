# spring-ai2-example

Spring AI 2 的特性示例集合，每个特性对应一个独立模块。

项目环境
- Spring AI 2.1.0-M1
- JDK 21
- SpringBoot 4.2.0

通用技术栈（各模块 application.yaml）
- 对话模型：阿里云百炼 DashScope（OpenAI 兼容模式）`qwen-plus`，本地 Ollama `qwen3-vl:4b`
- 向量模型：`text-embedding-v4`
- 向量数据库：Redis

目录
- [spring-ai2-example-helloword](spring-ai2-example-helloword)
  - HelloWord 项目，ChatClient 最基础的同步对话与流式对话（Flux）
  - 接口：`/hello`、`/openAiChatDemo`、`/ollamaChatDemo`、`/openAiChatStreamResult`
- [spring-ai2-example-usage-handling](spring-ai2-example-usage-handling)
  - usage handling 示例：从 `ChatResponse.getMetadata()` 读取 token 用量（`Usage`）、限流信息（`RateLimit`）与 prompt 缓存指标
  - 接口：`/usage/metadata`（ChatModel 直调完整元数据）、`/usage/chat-client-usage`（ChatClient 拿 Usage）、`/usage/rate-limit`（限流 + EmptyRateLimit 判空）、`/usage/cache-metrics`（缓存指标 + 原生用量对象）、`/usage/multi-turn-note`（多步流程累计用量）
- [spring-ai2-example-advisor](spring-ai2-example-advisor)
  - advisor 示例：自定义 `MySimpleLoggerAdvisor`（同时实现 `CallAdvisor` / `StreamAdvisor`，打印请求与响应日志，通过 `getOrder()` 控制执行顺序）
- [spring-ai2-example-prompt](spring-ai2-example-prompt)
  - prompt 示例：系统提示词（System Prompt）与角色设定
  - 接口：`/openAiChatSystemPrompt`（带系统提示词的对话）
- [spring-ai2-example-structured-output](spring-ai2-example-structured-output)
  - 结构化输出示例：通过 `.entity(...)` 将模型回答直接映射为 Java 对象
  - 接口：`/structured-output/ask1`（映射为 record）、`/structured-output/ask2`（`ParameterizedTypeReference` 泛型 List）
- [spring-ai2-example-chat-memory](spring-ai2-example-chat-memory)
  - chat memory 示例：通过 `ChatMemory` + 会话 ID（`ChatMemory.CONVERSATION_ID`）实现多轮对话记忆
  - 接口：`/openAiChatClientWithMemoryDemo?question=&convId=`（相同 convId 共享同一份上下文）
- [spring-ai2-example-tools](spring-ai2-example-tools)
  - tools 示例：`@Tool` / `@ToolParam` 注解声明工具，`.tools(...)` 注册后由模型按需调用（获取当前时间、设置闹钟）
  - 接口：`/openAiChatWithToolsDemo?question=`
- [spring-ai2-example-embedding](spring-ai2-example-embedding)
  - embedding 示例：调用 `OpenAiEmbeddingModel.embed()` 将文本转向量，并计算欧氏距离对比文本相似度
  - 接口：`/emDemo`
- [spring-ai2-example-vector-store](spring-ai2-example-vector-store)
  - 基于 Redis 的 vector store 示例：文档入库 `add()` 与相似度检索 `similaritySearch()`
  - 接口：`/addDoc`、`/query`
- [spring-ai2-example-simple-rag](spring-ai2-example-simple-rag)
  - 本地文件的 Rag 示例：加载 `classpath:docs` 下的 txt/docx/md/pdf 文件，`TokenTextSplitter` 按 token 切分入库，再通过 `QuestionAnswerAdvisor`（topK=3、相似度阈值 0.7）实现检索增强问答
  - 接口：`POST /addDocs`（加载并入库）、`/similarityAsk`（纯检索）、`/ask_rag`（RAG 问答）
- [spring-ai2-example-mcp](spring-ai2-example-mcp)
  - [spring-ai2-example-mcp-server](spring-ai2-example-mcp/spring-ai2-example-mcp-server)
    - MCP 服务端：`@McpTool` 注解暴露工具（示例：模拟天气预报），端口 8080
  - [spring-ai2-example-mcp-client](spring-ai2-example-mcp/spring-ai2-example-mcp-client)
    - MCP 客户端：通过 `SyncMcpToolCallbackProvider` 连接上述 server，模型按需调用远端工具，端口 8081
    - 接口：`/debug/mcp-tools`（查看已注册工具）、`/ai/mcp`（调用 MCP 工具回答）
  - 外部 MCP 服务接入示例（12306 火车票查询）
    - 通过 **stdio** 传输接入开源 [12306-mcp](https://github.com/Joooook/12306-mcp)：客户端启动时自动执行 `npx -y 12306-mcp` 拉起子进程，无需手动启动外部服务（与本地 streamable-http 方式互为对照）
    - `My12306Controller` 演示**多步工具调用**：模型自主完成 `get-current-date`（解析"明天"）→ `get-station-code-of-citys`（城市转车站编码）→ `get-tickets`（按 G 字头 + 12 点后过滤查余票）
    - 使用 `McpToolFilter` 按连接名过滤工具集：同时连接天气服务与 12306 时，每个场景只把对应的工具子集交给模型
    - 接口：`/12306/tickets`（查"明天下午 12 点后北京→郑州高铁"）、`/12306/debug/tools`（按连接名分组列出全部外部工具）
  - 惰性连接（类似 Dubbo 的 `check=false`）
    - `spring.ai.mcp.client.initialized=false`：启动阶段不与各 MCP 服务端做 initialize 握手，服务端不在线也能正常启动
    - 连接推迟到首次真正调用工具时才建立（MCP SDK `LifecycleInitializer` 的惰性初始化），故障从"启动期"推迟到"调用期"
    - 注意：stdio 传输首次拉起 npx 子进程需下载依赖，`request-timeout` 已适当加长（120s）
- [spring-ai2-example-multimodality](spring-ai2-example-multimodality)
  - 多模态示例：`.media(...)` 携带图片发起多模态请求，流式输出图片理解结果
  - 接口：`/analyze-image`

> 注意：运行前请在各模块 `application.yaml` 中配置自己的 API Key（`spring.ai.openai.api-key`）。
