# Spring AI 面试题

## 1. Spring AI 是什么？它解决了什么问题？

**答案：** Spring AI 是 Spring 官方推出的 AI 应用开发框架，借鉴了 Spring 生态的设计理念（如 Spring Data 的可移植性抽象、Spring Cloud 的抽象解耦思想）。

解决的核心问题：
- **统一 API 抽象**：不同 AI 厂商（OpenAI、Anthropic、Ollama、阿里 DashScope 等）的 API 各不相同，Spring AI 提供统一的 `ChatClient`/`ChatModel` 接口，切换厂商只需更换依赖和配置。
- **降低集成成本**：为 Embedding、向量数据库、RAG、函数调用、Advisors 等提供标准化组件。
- **与 Spring 生态无缝整合**：自动配置、依赖注入、可观测性（Micrometer）、安全性等开箱即用。

## 2. ChatClient 和 ChatModel 的区别是什么？

**答案：**
- **ChatModel**：底层模型调用接口，对应各厂商的具体实现（如 `OpenAiChatModel`），定义了 `call()` 等基础方法，是对"模型本身"的抽象。它类似于 JDBC 中的 `Connection`/`Driver` 层。
- **ChatClient**：面向开发者的高层 API，屏蔽了与模型交互的复杂性，提供流式 API（`prompt().user(...).call()`），支持 Prompt 模板、结构化输出、Advisors 链等。它类似于 JDBC 之上的 `JdbcTemplate`。

```java
@Bean
ChatClient chatClient(ChatClient.Builder builder) {
    return builder.defaultSystem("你是一个Java技术面试官").build();
}

String answer = chatClient.prompt()
        .user("请解释虚拟线程")
        .call()
        .content();
```

## 3. Spring AI 中的 Prompt 由哪些部分组成？System Message 和 User Message 的作用是什么？

**答案：** Prompt 主要由以下部分组成：
- **SystemMessage**：设定 AI 的角色、行为准则和约束，例如"你是一个严谨的 Java 面试官，回答控制在 300 字以内"。用户通常不可见。
- **UserMessage**：用户的实际输入问题。
- **AssistantMessage**：模型的历史回复（多轮对话时携带）。
- **ToolResponseMessage**：函数调用（工具）的返回结果。
- **Media**：多模态内容（图片、音频等）。

PromptTemplate 基于 StringTemplate（ST4）语法渲染占位符：
```java
PromptTemplate template = new PromptTemplate("用{style}的方式解释{topic}");
Prompt prompt = template.create(Map.of("style", "通俗", "topic", "RAG"));
```

## 4. 什么是结构化输出（Structured Output）？Spring AI 如何实现？

**答案：** 结构化输出是指让 LLM 的回复从自由文本变为符合指定类型（JSON → Java 对象/枚举/List）的能力。

Spring AI 的实现机制：
- **参数转换**：调用 `entity(Class<T>)` 时，框架在 Prompt 中自动附加格式说明（基于 `BeanOutputConverter` 生成的 JSON Schema 描述），引导模型按指定结构返回。
- **响应解析**：收到响应后，用 Jackson 反序列化为目标类型（支持 Record、POJO、`List<T>`、`Map` 等）。
- **三种转换器**：`BeanOutputConverter`（转 JavaBean）、`ListOutputConverter`（转列表）、`MapOutputConverter`（转 Map）。

```java
record InterviewQuestion(String question, String answer, int difficulty) {}

InterviewQuestion q = chatClient.prompt()
        .user("出一道Java21虚拟线程面试题")
        .call()
        .entity(InterviewQuestion.class);
```

注意点：不同模型遵循 JSON 格式指令的能力不同，复杂结构建议配合支持 JSON Mode / Tool Calling 的模型使用，解析失败会有重试机制（可通过 `RetryTemplate` 配置）。

## 5. 什么是 Advisors？Spring AI 的 Advisor 链机制是怎样的？

**答案：** Advisor 是 Spring AI 1.0 引入的核心扩展机制，灵感来自 Spring AOP 的拦截器模型，用于在 AI 调用链的请求（before）和响应（after）阶段插入自定义逻辑。

- **CallAdvisor**：拦截非流式调用；**StreamAdvisor**：拦截流式调用。
- **执行顺序**：按 `getOrder()` 值组成责任链，请求阶段按顺序执行，响应阶段逆序返回，形成洋葱模型。
- **AdvisedRequest / AdvisedResponse**：包装了原始请求/响应及上下文 `adviseContext`，可在 Advisor 间传递数据。

内置常用 Advisor：
- `MessageChatMemoryAdvisor`：将对话历史注入消息列表，实现多轮记忆。
- `QuestionAnswerAdvisor`：RAG 场景核心，自动检索向量库并拼接上下文。
- `PromptChatMemoryAdvisor`：将记忆拼接进 System Prompt。
- `SimpleLoggerAdvisor`：打印请求/响应日志。

```java
chatClient.prompt()
    .user(question)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
    .advisors(new QuestionAnswerAdvisor(vectorStore))
    .call().content();
```

## 6. Spring AI 如何实现会话记忆（Chat Memory）？

**答案：** Spring AI 提供 `ChatMemory` 接口抽象，按 conversationId 存储和检索对话历史。

- **存储实现**：`InMemoryChatMemory`（内置，基于 ConcurrentHashMap）、以及通过 starter 集成的 JDBC、Cassandra、Neo4j 等持久化实现。
- **注入方式**：`MessageChatMemoryAdvisor` 把历史消息作为独立消息注入；`PromptChatMemoryAdvisor` 把历史文本拼接到 System Prompt 中。
- **窗口控制**：`MessageWindowChatMemory` 保留最近 N 条消息（默认 20），防止超出模型上下文窗口。
- **隔离**：通过 advisor 参数 `ChatMemory.CONVERSATION_ID` 区分不同会话。

## 7. Spring AI 中 RAG 的实现流程是怎样的？涉及哪些核心组件？

**答案：** RAG（Retrieval-Augmented Generation，检索增强生成）流程：

**离线阶段（ETL）**：
1. **DocumentReader**：读取文档（`TextReader`、`PdfDocumentReader`、`TikaDocumentReader` 等）转为 `Document` 对象。
2. **DocumentTransformer**：切分（`TokenTextSplitter`、`TextSplitter`），将长文档切分为合适大小的 chunk，并附加元数据。
3. **DocumentWriter**：写入存储（`VectorStoreWriter`），EmbeddingModel 将 chunk 向量化后存入向量库。

**在线阶段（查询）**：
1. 用户提问，`QuestionAnswerAdvisor`（或自定义 Advisor）调用 `EmbeddingModel` 将问题向量化。
2. 通过 `SearchRequest` 在 `VectorStore` 中执行相似度检索（`similaritySearch`），取 Top-K 相关文档。
3. 将检索到的内容注入 Prompt 上下文，与用户问题一起发给模型生成答案。

```java
// 写入
vectorStore.add(List.of(new Document("内容...", Map.of("source", "java21.md"))));

// 检索问答
String answer = chatClient.prompt()
        .user(question)
        .advisors(new QuestionAnswerAdvisor(vectorStore,
                SearchRequest.builder().topK(5).similarityThreshold(0.7).build()))
        .call().content();
```

## 8. Spring AI 的函数调用（Function Calling / Tool Calling）是什么？如何使用？

**答案：** 函数调用允许 LLM 在需要时"请求"应用执行预定义的函数（工具），实现模型与现实世界（数据库、API、计算器）的交互。模型本身不执行函数，而是输出函数名和参数 JSON，由应用执行后将结果回传给模型继续推理。

Spring AI 1.0 中用 `@Tool` 注解声明工具：
```java
class WeatherTools {
    @Tool(description = "获取指定城市的当前天气")
    String getWeather(@ToolParam(description = "城市名") String city) {
        return weatherService.query(city);
    }
}

// 注册到 ChatClient
ChatClient chatClient = builder
    .defaultTools(new WeatherTools())
    .build();
```

旧版本写法为 `@Bean` 注册 `FunctionCallback` 并在 prompt options 中通过 `.functions("getWeather")` 启用。工具执行流程由框架自动编排：模型发起 `tool_calls` → 框架执行本地方法 → 结果以 `ToolResponseMessage` 回传 → 模型生成最终回答。

## 9. EmbeddingModel 与 VectorStore 的关系是什么？Spring AI 支持哪些向量数据库？

**答案：**
- **EmbeddingModel**：负责把文本转为高维浮点向量（如 `OpenAiEmbeddingModel`、`OllamaEmbeddingModel`），是"向量化"能力的抽象。
- **VectorStore**：负责向量的持久化和相似度检索（如 `SimpleVectorStore`、`PgVectorStore`、`MilvusVectorStore`），内部依赖 EmbeddingModel 对查询文本做向量化后再检索。

支持的主流向量库：PGvector、Redis、Milvus、Qdrant、Chroma、Weaviate、Elasticsearch、Pinecone、MongoDB Atlas 等，全部抽象为统一的 `VectorStore` 接口，通过对应 starter 集成。

## 10. Spring AI 如何支持多模型切换与流式输出？

**答案：**
- **多模型**：每个模型有独立的 starter 依赖与自动配置，`spring.ai.openai.*`、`spring.ai.ollama.*` 等命名空间隔离配置。同类型多模型时用 `@Qualifier` 注入具体 `ChatModel`；`ChatClient.Builder` 可基于指定模型构建。结合 Spring Cloud 的 `spring.config.import` 配置中心可实现运行时切换。
- **流式输出**：`stream()` 方法返回 `Flux<String>`（基于 Reactor），底层使用 SSE（Server-Sent Events）推送给前端：
```java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestParam String q) {
    return chatClient.prompt().user(q).stream().content();
}
```
注意流式场景需使用 `StreamAdvisor`，且部分组件（如某些结构化输出）在流式下支持有限。
