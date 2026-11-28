# Prompt（提示词）学习笔记

> 对应模块：`spring-ai2-example-prompt`
> 参考文档：https://docs.spring.io/spring-ai/reference/api/prompt.html

## 先聊聊：Prompt 到底是个啥

说白了，Prompt 就是你发给大模型的"话"。

同样一个模型，你话怎么组织、怎么说，出来的效果天差地别。所以玩好 AI 应用，第一件事就是把 Prompt 这件事搞明白。

Spring AI 处理 Prompt 的思路，你可以想象成 Spring MVC 处理"视图"：写一大段带占位符的文本，然后程序运行时把真实数据填进去。

就好比一条带 `?` 占位符的 SQL，模板是死的，参数是活的。

## 三个核心概念，一图看懂

```
Prompt（发出去的完整请求）
 ├── Message（一条条的消息，每条都有自己的"角色"）
 │    ├── SystemMessage   → 系统：给模型立人设、定规矩
 │    ├── UserMessage     → 用户：真正的问题
 │    ├── AssistantMessage → 助手：模型之前说的话（用于延续对话）
 │    └── ToolResponseMessage → 工具：工具调用的返回结果
 └── ChatOptions（可选：温度、模型名等参数）
```

### 消息角色（Roles）

模型不是拿到一坨文本就开算，它认"角色"。四种角色各干各的活：

| 角色 | 干什么的 | 生活化理解 |
|------|---------|-----------|
| System | 给 AI 立规矩、定风格，对话开始前就生效 | 面试前 HR 给你的"候选人须知" |
| User | 用户的输入，问题本身 | 你开口问的话 |
| Assistant | AI 之前的回答 | 对话记录里 AI 那一方 |
| Tool | 工具调用的返回值 | AI 打电话问完查号台后拿到结果 |

为什么要分角色？因为你把"规矩"和"问题"分开写，模型才知道哪些是底线不能碰、哪些是这次要回答的。混在一起写，效果就会打折扣。

### PromptTemplate（模板）

带 `{xxx}` 占位符的字符串，填上参数就能变成完整的 Prompt：

```java
PromptTemplate promptTemplate = new PromptTemplate("讲一个{adjective}的笑话，主题是{topic}");
Prompt prompt = promptTemplate.create(Map.of("adjective", "冷笑话", "topic", "程序员"));
```

`{adjective}` 和 `{topic}` 就是挖好的坑，`Map.of(...)` 就是往坑里填土。

默认用 StringTemplate 引擎渲染，`{}` 是默认分隔符。

## 文档里的 6 个案例（模块里全部能跑）

启动模块后逐个试（默认端口 8080）：

```bash
cd spring-ai2-example-prompt
./mvnw spring-boot:run
```

### 案例 1：基础模板 — GET /prompt/template-basic

文档原型：`"Tell me a {adjective} joke about {topic}"`

```java
PromptTemplate promptTemplate = new PromptTemplate("讲一个{adjective}的笑话，主题是{topic}");
Prompt prompt = promptTemplate.create(Map.of("adjective", adjective, "topic", topic));
return openAiChatModel.call(prompt).getResult().getOutput().getText();
```

试试：`curl -G "http://localhost:8080/prompt/template-basic" --data-urlencode "adjective=冷笑话" --data-urlencode "topic=程序员"`

你会看到：模型真的讲了个程序员冷笑话（Oct 31 == Dec 25 那个）。

**要点**：

- 这里没用 ChatClient，直接用的 `ChatModel.call(Prompt)`——这是 Spring AI 最底层的调用姿势。
- 文档说 ChatClient 之于 ChatModel，就像 JdbcClient 之于 JDBC。
- 想搞清楚底层，从这个入口看最合适。

### 案例 2：System + User 组合拳 — GET /prompt/template-roles

这是文档 "Roles" 一节的核心示例：System 立人设，User 提问题，打包成一条 Prompt。

```java
Message userMessage = new UserMessage("给我讲讲" + topic + "的核心概念");

SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate("""
        你是一个乐于助人的 AI 助手。
        你的名字是{name}。
        你要用{voice}的风格来回答用户的问题。
        """);
Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "小智", "voice", "单口相声演员"));

Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
```

试试：`curl -G "http://localhost:8080/prompt/template-roles" --data-urlencode "topic=向量数据库"`

你会看到：模型真的用单口相声的口吻回答，开头还报了"小智"的名号。

**要点**：

- `SystemPromptTemplate` 是专门渲染 system 消息的模板类。
- 多条消息用 `List.of(...)` 按顺序组合，顺序一般 system 在前。

### 案例 3：Assistant 消息 + 少样本示例 — GET /prompt/template-assistant

文档说 Assistant 角色的作用是"保持对话连贯"。实战里最常见的用法是：给模型看一问一答的示例，让它照葫芦画瓢（也就是 Few-shot）。

```java
Message userExample = new UserMessage("书籍：《三体》");
Message assistantExample = new AssistantMessage("《三体》：硬科幻巅峰之作……强烈推荐。");
Message userQuestion = new UserMessage("书籍：" + bookName);

// System → 示例对 → 真实问题
Prompt prompt = new Prompt(List.of(systemMessage, userExample, assistantExample, userQuestion));
```

试试：`curl -G "http://localhost:8080/prompt/template-assistant" --data-urlencode "bookName=深入理解Java虚拟机"`

你会看到：输出的评价风格和字数明显在模仿示例（"JVM领域权威经典，原理与实践并重，字字珠玑，强烈推荐"）。

**要点**：

- AssistantMessage 不只是"历史记录"，它还能当"示范"用。
- Few-shot 的本质：与其解释格式，不如直接给样例。

### 案例 4：自定义分隔符，专治 JSON 冲突 — GET /prompt/template-delimiter

文档特别提醒：模板里要写 JSON 的话，默认的 `{}` 分隔符会和 JSON 的 `{}` 打架（渲染引擎会误以为 JSON 大括号是占位符）。

解法是换个分隔符，比如 `<` `>`：

```java
PromptTemplate promptTemplate = PromptTemplate.builder()
        .renderer(StTemplateRenderer.builder()
                .startDelimiterToken('<')
                .endDelimiterToken('>')
                .build())
        .template("""
                请严格按以下 JSON 格式输出：
                {"books": [{"title": "<title>", "reason": "<reason>"}]}
                请推荐一本关于<subject>的书籍并填充上述 JSON。
                """)
        .build();
```

试试：`curl "http://localhost:8080/prompt/template-delimiter"`

你会看到：模型严格输出了 JSON，且 `<title>` 等占位符被正确替换。

**要点**：

- JSON 的 `{}` 原样保留，占位符换成 `<title>` 这种写法。
- 这个坑不做示例很容易踩：模板里一写 JSON 就渲染报错，还以为是 StringTemplate 的 bug。

### 案例 5：模板写成文件，别硬编码 — GET /prompt/template-resource

把模板字符串写死在 Java 里，改一句话都要重新编译。

文档给的姿势是用 Spring 的 `Resource` 抽象，把模板放到文件里：

```java
@Value("classpath:/prompts/it-consultant-system-message.st")
private org.springframework.core.io.Resource systemResource;

// SystemPromptTemplate 可以直接吃 Resource
SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
Message systemMessage = systemPromptTemplate.createMessage(Map.of("role", role, "tone", tone));
```

模板文件在 `src/main/resources/prompts/it-consultant-system-message.st`。

试试：`curl -G "http://localhost:8080/prompt/template-resource" --data-urlencode "role=资深架构师" --data-urlencode "tone=严谨" --data-urlencode "question=微服务拆分有哪些原则？"`

你会看到：模型以"严谨的资深架构师"人设回答，先给结论再展开。

**要点**：

- `.st` 是 StringTemplate 的惯例后缀。
- 模板集中放 `resources/prompts/` 目录管理，改提示词不用动 Java 代码。
- 真实项目里 prompt 会反复调优，这个习惯能省很多事。

### 案例 6：只渲染不发请求 — GET /prompt/template-render-only

调试模板时经常只想看看占位符替换后的最终长什么样，不想花钱调模型：

```java
String rendered = promptTemplate.render(Map.of("adjective", adjective, "topic", topic));
```

试试：`curl -G "http://localhost:8080/prompt/template-render-only" --data-urlencode "adjective=冷笑话" --data-urlencode "topic=程序员"`

你会看到：`渲染后的 Prompt：讲一个冷笑话的笑话，主题是程序员`

**要点**：

- `render()` 只做字符串替换，不调模型（零成本）。
- `create()` 才会生成 Prompt 对象交给模型。

## 几个 API 的关系，别绕晕

| API | 一句话定位 |
|-----|-----------|
| `ChatModel.call(Prompt)` | 最底层：直接把 Prompt 扔给模型，本文所有案例都用它 |
| `ChatClient.prompt().user(...)` | 高层封装：链式 API，日常开发首选 |
| `PromptTemplate.render(model)` | 纯渲染，返回 String |
| `PromptTemplate.create(model)` | 渲染并包成 Prompt 对象，配合 ChatModel 用 |
| `SystemPromptTemplate.createMessage(model)` | 渲染并包成 system 角色的 Message |

`PromptTemplate` 实现了三个接口族，对应三种产出：

- `PromptTemplateStringActions` → 渲染成字符串（`render`）
- `PromptTemplateMessageActions` → 渲染成 Message（`createMessage`）
- `PromptTemplateActions` → 渲染成完整 Prompt（`create`）

## 写好 Prompt 的几个常识（文档 Prompt Engineering 一节的精华）

文档给了个"好 Prompt 四件套"：

1. **指令**：清楚直接地说要它干嘛，当人说话
2. **上下文**：必要的背景信息，别让模型瞎猜
3. **用户输入**：真正的问题
4. **输出格式**：要 JSON 就明说 JSON（但别指望它 100% 听话，它可能给你"这是您的 JSON：{...}"这种前缀）

另外几个技巧：

- 给示例（Few-shot）
- 让它一步步想（Chain-of-Thought）
- 复杂任务先分析再行动（ReAct）

文档还提了个有意思的研究结论：在 Prompt 里加一句"深呼吸，一步步来"都能提升解题效果——Prompt 的措辞真的有用。

## Token 是什么，为什么要在乎

模型不认识"字"，它处理的是 token（可以理解为 0.75 个词左右的碎片）。Shakespeare 全集约 90 万词 ≈ 120 万 token。

跟你相关的三件事：

- **计费按 token 算**：输入和输出都算钱，Prompt 越啰嗦越贵
- **模型有上下文窗口上限**：超出上限的内容直接不处理，别把一整本书塞进去问"哈姆雷特讲了啥"
- **响应的 metadata 里有 token 用量**：可以拿来监控成本

## 踩坑记录

- `Resource` 类名冲突：`jakarta.annotation.Resource`（注入注解）和 `org.springframework.core.io.Resource`（模板资源）同名。要么全限定名，要么注解改用 `@Autowired`。
- `ChatModel` 的包路径是 `org.springframework.ai.chat.model.ChatModel`，不是 `org.springframework.ai.chat.ChatModel`（老版本教程里的写法，2.x 已搬家）。
- 中文参数 curl 记得 `--data-urlencode`，直接拼 URL 会 400。
