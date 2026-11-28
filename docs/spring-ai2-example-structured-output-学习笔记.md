# Structured Output（结构化输出）学习笔记

> 对应模块：`spring-ai2-example-structured-output`
> 参考文档：
> - 主文档：https://docs.spring.io/spring-ai/reference/api/structured-output.html
> - 校验自纠错：https://docs.spring.io/spring-ai/reference/api/structured-output/validation.html
> - 服务商原生：https://docs.spring.io/spring-ai/reference/api/structured-output/native.html
> - 输出转换器：https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html

## 先聊聊：为什么需要结构化输出

大模型就是个"文本进、文本出"的黑盒。可你的 Java 代码要的是对象：拿到回复后要取字段、存数据库、走 if/else 分支。

于是问题来了：怎么把"一段话"变成"一个类型安全的 Java 对象"？

结构化输出就是干这个的。就好比海关报关：你提前告诉模型"货必须按这个清单格式摆"（JSON Schema），模型照单生产，Spring AI 验货后直接交给你一个能用的 Java 对象。

## 核心机制：一张图看懂

```
你的 Java 类型（record/class）
      │
      ▼ ① Spring AI 自动生成 JSON Schema
      │
ChatClient.prompt()...call().entity(MyType.class)
      │
      ▼ ② Schema 以格式指令的形式附进 prompt（默认方式）
      │
模型输出 JSON 文本
      │
      ▼ ③ 转换器解析 JSON，反序列化
      │
MyType 实例 —— 后续代码当普通对象用
```

一句话总结：`.entity()` 代替 `.content()`，前者给你对象，后者只给你文本。

## 主文档：3 个基础用法

启动模块后逐个试（默认端口 8080，模块里 `StructuredOutputController` 是早期基础版，`StructuredOutputDocsController` 是本次文档案例版）：

```bash
cd spring-ai2-example-structured-output
./mvnw spring-boot:run
```

### 基础 1：entity(Class) — 既有接口 /structured-output/ask1

文档原型：`record ActorsFilms(String actor, List<String> movies) {}` + `.entity(ActorsFilms.class)`

```java
ActorsFilms films = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorsFilms.class);
```

**要点**：

- 定义个 record，`.entity()` 传入类型，出来就是填好的对象。
- 底层自动做了三件事：生成 Schema → 塞进 prompt → 解析回对象。
- 所有模型通用，跟服务商无关。

### 基础 2：泛型用 ParameterizedTypeReference — 既有接口 /structured-output/ask2

文档原型：`.entity(new ParameterizedTypeReference<List<ActorsFilms>>() {})`

**要点**：

- `entity(Class)` 处理不了 `List<T>`、`Map<K,V>` 这种泛型。
- 泛型类型用 `ParameterizedTypeReference`（匿名子类写法）。

### 基础 3：responseEntity — 新增接口 /structured-output/docs/response-entity

文档原型：`ResponseEntity<ChatResponse, ActorsFilms> result = ...responseEntity(ActorsFilms.class)`

```java
var result = chatClient.prompt()...call().responseEntity(ActorsFilms.class);
ActorsFilms films = result.entity();    // 结构化对象
ChatResponse raw = result.response();   // 原始响应
long tokens = raw.getMetadata().getUsage().getTotalTokens();
```

试试：`curl "http://localhost:8080/structured-output/docs/response-entity"`

你会看到：`演员: Emily Chen / 电影数: 5 / 本次消耗 tokens: 235`

**要点**：

- `.entity()` 只返回对象，token 用量等元数据全丢了。
- 还要元数据时用 `.responseEntity()`，对象和 `ChatResponse` 一起拿。
- 与 usage-handling 模块的知识点接上了：元数据从 `result.response().getMetadata()` 里挖。

## Validation 文档：坏输出自动纠错 — /structured-output/docs/validate-schema

文档原型：`.entity(ActorsFilms.class, spec -> spec.validateSchema())`

自纠错循环的工作方式：

1. 模型响应。
2. Spring AI 按目标类型的 JSON Schema 校验。
3. 校验通过 → 直接返回对象。
4. 校验失败 → 把具体错误（如"缺少必填字段 actor"）追加到 prompt 重新请求。
5. 默认最多重试 3 次。

试试：`curl "http://localhost:8080/structured-output/docs/validate-schema"`

你会看到：`校验通过 → 演员: Emily Chen，电影: [Whispers of the Horizon, ...]`

**要点**：

- 默认 `.entity()` 是"尽力而为"：模型多半配合，偶尔夹带私货（多余字段/缺字段/包在散文里），解析直接炸。
- `validateSchema()` 的重试不是盲目重发：模型能看到上一次的具体错误，对症改。
- 背后是 `StructuredOutputValidationAdvisor`（递归 Advisor），开关注册即可，零配置。
- 要改重试次数等参数，自己 build 一个 advisor 注册到 ChatClient（`maxRepeatAttempts(5)`）。
- 流式（`.stream()`）不支持：校验需要完整响应。

## Native 文档：请求侧硬约束 — /structured-output/docs/provider-native

文档原型：`.entity(ActorsFilms.class, spec -> spec.useProviderStructuredOutput())`

两种约束方式的对比：

| | 默认 prompt 方式 | useProviderStructuredOutput() |
|---|---|---|
| 约束位置 | prompt 里塞格式说明文本 | API 级字段发给服务商 |
| 谁来保证 | 模型自觉遵守 | 服务商运行时强制（不合法的输出根本发不出来）|
| prompt 干净度 | 夹带 Schema 文本 | 无格式指令，更省 token |
| 兼容性 | 所有模型 | 仅支持原生结构化输出的模型 |

试试：`curl "http://localhost:8080/structured-output/docs/provider-native"`

你会看到：`原生约束生效 → 演员: Maya Chen，电影数: 15`

**要点**：

- 支持的模型：OpenAI GPT-4o+、Claude 3.5 Sonnet+、Gemini 1.5 Pro+、Mistral Small+、部分 Ollama 模型。
- 模型不支持时开关被**静默忽略**，回退到 prompt 方式——不会报错，但也没有更强保证。
- 默认关闭就是为了兼容性，要用得拿自己的模型实测。

### 顶层数组的坑 + 双开关组合 — /structured-output/docs/native-with-validation

文档原型：OpenAI 原生结构化输出**不支持顶层是数组**，直接要 `List<T>` 会 API 报错。

```java
// 解法：把 List 包进容器 record
record FilmographyList(List<ActorsFilms> films) {}

FilmographyList result = chatClient.prompt()...
    .entity(FilmographyList.class, spec -> spec
        .useProviderStructuredOutput()   // 请求侧约束
        .validateSchema());              // 响应侧兜底
```

试试：`curl "http://localhost:8080/structured-output/docs/native-with-validation"`

你会看到：`组合拳成功，共拿到 2 位演员的作品列表`

**要点**：

- 要 `List<T>` 又想开原生约束：包一层容器 record。
- 两个开关解决不同问题：原生约束管"尽量不出错"，校验重试管"出了错自动纠正"。
- 下游代码对格式零容忍时，两个一起上。

## Converters 文档：转换器（低阶 API）

`.entity()` 的底层就是 `StructuredOutputConverter`：调用前往 prompt 里追加格式指令（`getFormat()`），调用后把文本解析成目标类型（`convert()`）。

### 案例：MapOutputConverter 手动两阶段 — /structured-output/docs/map-converter-manual

文档原型：`MapOutputConverter` + `{format}` 占位符 + 手动 `convert()`。

```java
MapOutputConverter converter = new MapOutputConverter();
String format = converter.getFormat();  // 阶段一：格式指令塞进模板
String template = """
        Provide me a List of {subject}
        {format}
        """;
// ... ChatModel 调用拿文本 ...
Map<String, Object> result = converter.convert(text);  // 阶段二：手动解析
```

试试：`curl "http://localhost:8080/structured-output/docs/map-converter-manual"`

你会看到：`解析结果: {numbers=[1, 2, 3, 4, 5, 6, 7, 8, 9]}`

**要点**：

- 想看清"格式指令 → 文本 → 解析"全流程，用低阶手动姿势最直观。
- 高阶 `.entity()` 把这两步自动做了，所以你平时感知不到 converter 的存在。

### 案例：ListOutputConverter — /structured-output/docs/list-converter

文档原型：`.entity(new ListOutputConverter(new DefaultConversionService()))`

试试：`curl "http://localhost:8080/structured-output/docs/list-converter"`

你会看到：`冰淇淋口味: [Vanilla, Chocolate, Strawberry, Mint Chocolate Chip, Cookies and Cream]`

**要点**：

- 适合"列 5 个xxx"这类轻量场景，输出是逗号分隔文本，根本不用 JSON。
- 三个内置转换器怎么选：要对象用 `BeanOutputConverter`，要 Map 用 `MapOutputConverter`，要简单列表用 `ListOutputConverter`。

### 案例：自定义宽松转换器 — /structured-output/docs/lenient-converter

文档痛点：`BeanOutputConverter` 很严格，但模型喜欢把 JSON 包在 markdown 代码围栏里，第一个字母就让它解析爆炸。

```java
class LenientJsonOutputConverter<T> implements StructuredOutputConverter<T> {
    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private final BeanOutputConverter<T> delegate;

    @Override
    public T convert(String source) {
        Matcher matcher = FENCE.matcher(source);
        String json = matcher.find() ? matcher.group(1).trim() : source.trim();
        return delegate.convert(json);  // 剥完围栏委托给默认解析器
    }

    @Override
    public String getJsonSchema() {
        return delegate.getJsonSchema();  // 委托回去，两个可靠性开关照常工作
    }
}
```

试试：`curl "http://localhost:8080/structured-output/docs/lenient-converter"`

你会看到：`宽松解析成功 → 演员: Aisha Reynolds...，电影数: 5`（模型这次确实先废话了一段再输出 JSON，宽松解析扛住了）

**要点**：

- 自定义 converter 的姿势：剥掉杂质后委托内置解析器，别自己重写 JSON 反序列化。
- `getJsonSchema()` 是 2.0 加的桥接方法：委托回去，`validateSchema()` / `useProviderStructuredOutput()` 对自定义 converter 照样生效；不实现则两个开关对它失效。
- 非 JSON 格式（YAML/CSV）也从这里进：自己写 `getFormat()` 和 `convert()`。

## 几个 API 的关系，别绕晕

| API | 一句话定位 |
|-----|-----------|
| `.entity(Type.class)` | 默认姿势：prompt 方式，全模型通用 |
| `.entity(new ParameterizedTypeReference<List<T>>(){})` | 泛型类型专用 |
| `.entity(Class, spec -> spec.validateSchema())` | 响应侧校验 + 带错误重试（最多 3 次）|
| `.entity(Class, spec -> spec.useProviderStructuredOutput())` | 请求侧 API 级约束，需模型支持 |
| `.responseEntity(Class)` | 对象 + ChatResponse 元数据一起拿 |
| `.entity(converter实例)` | 传入自定义/内置转换器 |
| `StructuredOutputConverter<T>` | 低阶接口：`getFormat()` + `convert()` + `getJsonSchema()` |
| `BeanOutputConverter` | 内置：JSON → Java Bean/record |
| `MapOutputConverter` | 内置：JSON → Map |
| `ListOutputConverter` | 内置：逗号分隔文本 → List |
| `StructuredOutputValidationAdvisor` | validateSchema() 背后的递归 Advisor |

## 一张决策表：我该用哪个

按文档 Cheat Sheet 整理：

- 默认场景，模型不重要 → `.entity(Type.class)`
- List/Map 泛型 → `ParameterizedTypeReference`
- 坏输出不能忍 → 加 `validateSchema()`
- 模型支持原生约束，要更强保证 → 加 `useProviderStructuredOutput()`
- 两者都要 → 链式叠加两个开关
- 还要 token 用量 → 换 `.responseEntity()`
- JSON 被围栏包裹 / 非 JSON 格式 → 自定义 `StructuredOutputConverter`
- 流式 `.stream()` → 都不支持，只回文本块

## 踩坑记录

- 模型不总是听话：qwen-plus 实测会无视格式指令，输出"The numbers are..."这类前缀文字，`MapOutputConverter` 直接 `StreamReadException: Unrecognized token 'The'`。解法：补一条 system 强约束"只输出 JSON 本身"。这也印证了文档那句"best effort"。
- `ResponseEntity` 类名冲突：Spring AI 的 `org.springframework.ai.chat.client.ResponseEntity` 和 Spring MVC 的同名类冲突，同文件里要用全限定名。
- DashScope 下原生约束是"静默降级"：qwen-plus 走兼容模式，`useProviderStructuredOutput()` 不会报错但也没有 API 级强制——想要真原生约束得确认服务商支持。
- 输出字段的"污染"：宽松解析案例里，模型把介绍文字写进了 actor 字段（"Aisha Reynolds is a versatile..."）。字段级的内容质量靠 schema 管不住，必要时配合 prompt 约束或后置校验。
