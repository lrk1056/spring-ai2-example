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
    - 使用前需先启动 mcp-server
- [spring-ai2-example-multimodality](spring-ai2-example-multimodality)
  - 多模态示例：`.media(...)` 携带图片发起多模态请求，流式输出图片理解结果
  - 接口：`/analyze-image`

> 注意：运行前请在各模块 `application.yaml` 中配置自己的 API Key（`spring.ai.openai.api-key`）。
