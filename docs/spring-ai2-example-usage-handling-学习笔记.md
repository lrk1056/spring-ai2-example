# Usage Handling（用量元数据）学习笔记

> 对应模块：`spring-ai2-example-usage-handling`
> 参考文档：https://docs.spring.io/spring-ai/reference/api/usage-handling.html

## 先聊聊：为什么要关心这个东西

你每调一次大模型，都是在花钱。服务商按 token 计费，输入算钱、输出也算钱。

但很多 demo 代码写完就完了，压根不知道一次调用烧了多少 token，更说不清这个月账单是怎么涨起来的。

Spring AI 把服务商返回的用量数据封装成了几个类型，让你随时能"看账单"。

就好比手机流量监控：不光告诉你用了多少（Usage），还告诉你套餐限额和剩余量（RateLimit）。

## 三个核心类型，一张图看懂

```
ChatResponse（模型返回的完整响应）
 └── getMetadata() → ChatResponseMetadata（总入口）
       ├── getId()            → 本次响应的 ID
       ├── getModel()         → 实际用的模型名
       ├── getUsage()         → Usage：这次烧了多少 token
       │     ├── getPromptTokens()      输入（你发过去的内容）
       │     ├── getCompletionTokens()  输出（模型回的内容）
       │     ├── getTotalTokens()       两者之和
       │     ├── getNativeUsage()       服务商原生的用量对象
       │     ├── getCacheReadInputTokens()   缓存命中 tokens（可能 null）
       │     └── getCacheWriteInputTokens()  缓存写入 tokens（可能 null）
       └── getRateLimit()     → RateLimit：套餐限额/余额/重置时间
```

一句话：`ChatResponseMetadata` 是总入口，`Usage` 管花钱，`RateLimit` 管限流。

## 文档里的 5 个案例（模块里全部能跑）

启动模块后逐个试（默认端口 8080）：

```bash
cd spring-ai2-example-usage-handling
./mvnw spring-boot:run
```

### 案例 1：ChatModel 直调，看完整账单 — GET /usage/metadata

文档原型：`chatModel.call(prompt)` 后从 `response.getMetadata()` 拿一切。

```java
ChatResponse response = openAiChatModel.call(new Prompt(question));
ChatResponseMetadata metadata = response.getMetadata();
Usage usage = metadata.getUsage();
usage.getPromptTokens();      // 输入
usage.getCompletionTokens();  // 输出
usage.getTotalTokens();       // 合计
```

试试：`curl -G "http://localhost:8080/usage/metadata" --data-urlencode "question=用一句话介绍Redis"`

你会看到（真实输出）：

```
响应 ID: chatcmpl-4fdb98a5-a8e4-9c73-9351-5ff57c50630d
实际模型: qwen3.7-plus
输入 tokens(prompt): 14
输出 tokens(completion): 295
总 tokens(total): 309
```

**要点**：

- 输入 14 个 token，输出 295 个——输出比输入贵 20 倍。
- 这就是为什么"让模型少废话"能直接省钱。

### 案例 2：ChatClient 场景拿 Usage — GET /usage/chat-client-usage

文档原型：ChatClient 的 `.call()` 后调 `.chatResponse()`。

```java
ChatResponse response = openAiChatClient.prompt()
        .user(question)
        .call()
        .chatResponse();  // 关键：不是 content()！
Usage usage = response.getMetadata().getUsage();
```

试试：`curl -G "http://localhost:8080/usage/chat-client-usage" --data-urlencode "question=一句话介绍Spring AI"`

你会看到：回答正文 + `本次消耗 tokens: 664（输入 14 / 输出 650）`

**要点**：

- 日常用 ChatClient 时最容易踩的坑：`.content()` 只返回文本字符串，元数据直接丢了。
- 想要用量，就得用 `.chatResponse()` 拿完整响应对象。
- 流式接口（`.stream()`）同理，得从聚合后的响应里拿。

### 案例 3：RateLimit + EmptyRateLimit 判空 — GET /usage/rate-limit

文档原型：`metadata.getRateLimit()` 拿限流数据（来自服务商的 HTTP 响应头）。

```java
RateLimit rateLimit = response.getMetadata().getRateLimit();
if (rateLimit instanceof EmptyRateLimit) {
    // 服务商没给这数据，不是"剩余为 0"
}
```

试试：`curl -G "http://localhost:8080/usage/rate-limit" --data-urlencode "question=hi"`

你会看到：`当前服务商未返回限流信息（EmptyRateLimit），不是真的剩余额度为 0`

**要点**：

- 这是个反直觉的设计：拿不到限流数据时，返回的是**全是 0 的 EmptyRateLimit 对象，不是 null**。
- 所以只判 null 会漏——要用 `instanceof EmptyRateLimit` 区分"没这数据"和"真的用光了"。
- OpenAI 官方 API 会透出限流头；DashScope 兼容模式没有，所以这里走了 EmptyRateLimit 分支。

### 案例 4：Prompt 缓存指标 — GET /usage/cache-metrics

文档原型：`getCacheReadInputTokens()` / `getCacheWriteInputTokens()` 跨服务商统一读缓存指标。

```java
Usage usage = response.getMetadata().getUsage();
Long cacheRead = usage.getCacheReadInputTokens();   // 不支持的服务商返回 null
Long cacheWrite = usage.getCacheWriteInputTokens();
```

试试：`curl -G "http://localhost:8080/usage/cache-metrics" --data-urlencode "question=hi"`

你会看到：缓存读 0、缓存写 null，还有一坨原生对象输出：

```
原生用量对象: CompletionUsage{completionTokens=233, promptTokens=11, ...,
  reasoningTokens=221, cachedTokens=0, ...}
```

**要点**：

- `getNativeUsage()` 能拿到服务商原生的用量对象——这里 DashScope 兼容模式返回的是 OpenAI SDK 的 `CompletionUsage`。
- 注意 `reasoningTokens=221`：qwen3.7-plus 是思考模型，光"想"就花了两百多个 token，这部分也计费。
- 缓存指标的支持情况：Anthropic/OpenAI 支持，Ollama/DeepSeek 不支持。
- 不支持的服务商，这两个方法返回 null。

### 案例 5：多步流程的累计用量 — GET /usage/multi-turn-note

文档核心结论：工具调用循环会发起**多轮**模型调用，`getUsage()` 返回的是所有轮次的**加总**，不是最后一轮的。加总逻辑由 `UsageCalculator` 完成。

```java
// 工具调用场景：模型先想 -> 调工具 -> 再想 -> 回答，至少两轮模型调用
ChatResponse response = chatClient.prompt("巴黎天气如何？")
        .tools(new WeatherTools())
        .call()
        .chatResponse();
usage.getTotalTokens();  // 所有轮次的总和
```

试试：`curl "http://localhost:8080/usage/multi-turn-note"`

你会看到：`单轮 tokens: 389（nativeUsage=有）`、`两轮合计 tokens: 3434`

**要点**：

- 多步流程下 `getNativeUsage()` 会返回 **null**——原生对象没法跨响应合并。
- 想看原生对象，只能拆成单次 ChatModel 调用。
- 统计成本别以为"一次请求一轮调用"：带工具的对话实际是 N 轮，账单按总和算。

## 几个 API 的关系，别绕晕

| API | 一句话定位 |
|-----|-----------|
| `response.getMetadata()` | 总入口，拿到 ChatResponseMetadata |
| `metadata.getUsage()` | 这次调用烧了多少 token（最常用）|
| `usage.getTotalTokens()` | 输入+输出合计，监控/计费就用它 |
| `usage.getNativeUsage()` | 服务商原生对象（多步流程下为 null）|
| `usage.getCacheReadInputTokens()` | 缓存命中数，不支持的厂商返回 null |
| `metadata.getRateLimit()` | 套餐限额/余额，拿不到时是 EmptyRateLimit 不是 null |
| `chatClient...call().chatResponse()` | ChatClient 里拿完整响应的唯一姿势 |
| `chatClient...call().content()` | 只要文本，元数据丢失 |
| `UsageCalculator.getCumulativeUsage(...)` | 框架内部用它做多轮加总 |

## 什么场景会真的用到

- **成本监控**：把 `getTotalTokens()` 埋点上报，按用户/接口维度算账
- **限流保护**：拿到 `RateLimit` 后在应用层自己做熔断（余量低于阈值就排队）
- **Prompt 瘦身**：发现 `getPromptTokens()` 异常大，说明塞了太多没用的上下文
- **模型对比**：同一个问题换不同模型跑，对比 token 消耗和 reasoningTokens
- **缓存收益**：看 `cacheReadInputTokens` 评估服务商的 prompt 缓存帮你省了多少

## 踩坑记录

- `.content()` 是元数据黑洞：ChatClient 链式调用最后一步如果调了 `.content()`，token 用量就再也拿不到了。要用 `.chatResponse()`。
- EmptyRateLimit 不是 null：判空要 `instanceof EmptyRateLimit`，光判 null 会把"没数据"误判成"额度为 0"。
- DashScope 兼容模式不透出限流头：`getRateLimit()` 永远是 EmptyRateLimit，想要真实限流数据只能接 OpenAI 官方或看服务商自己的控制台。
- 思考模型的 token 账：qwen3.7-plus 这类思考模型，`reasoningTokens` 计入 completion 计费但用户看不到内容，成本评估时别只看最终回答长度。
