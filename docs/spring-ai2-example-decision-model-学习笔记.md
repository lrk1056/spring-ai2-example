# Decision Models（决策模型）学习笔记

> 对应模块：`spring-ai2-example-decision-model`
> 参考文档：https://docs.spring.io/spring-ai/reference/api/decision.html
> 说明：模块代码中的输入与问题均已改为中文（语义与文档英文原版一致），本文摘录的是中文实测输出。

## 先聊聊：决策模型是个啥

你肯定遇到过这种需求：判断用户消息是投诉还是咨询、判断这条内容要不要放行、给客服消息分个优先级。

用聊天模型做也行，但它回你一段自由发挥的文字，你还得自己从里面抠出"是"还是"否"——费 token 还不稳定。

决策模型就是专门干这个的。就好比选择题和问答题的区别：聊天模型做问答题，自由发挥；决策模型做选择题，只能从你给的候选项里挑一个，还得告诉你每个选项的概率。

- **更快更便宜**：同样一个判断，决策模型比聊天模型快得多、便宜得多。
- **概率可调阈值**：它报告每个结果的概率，你的代码决定"多确定才动手"。
- **它不是规则引擎**：答案是概率性的，可能出错。错一次代价大的场景（比如自动退款），要么加人工复核，要么设个安全的默认值。

## 核心概念：一张图看懂

```
DecisionPrompt（一次决策请求）
 ├── 输入 DecisionInput（三种任选）
 │    ├── text()          → 一段文本（如用户消息）
 │    ├── fields()        → 命名字段（文本+数字+布尔，模型知道每个值啥意思）
 │    └── conversation()  → 一段对话（检查"最后一条回复对不对"）
 ├── 问题（三种，一次可以混着问多个）
 │    ├── YesNoQuestion   → 是非题   → YesNoAnswer（Yes 的概率）
 │    ├── ChoiceQuestion  → 选择题   → ChoiceAnswer（选中项+各选项概率）
 │    └── ScoreQuestion   → 打分题   → ScoreAnswer（级别+各级别概率）
 └── options()            → 本次调用的设置（如换更大的模型）

DecisionResponse（响应）
 ├── getYesNo(name) / getChoice(name) / getScore(name)  → 按名字取答案
 ├── isRefused(name)                                     → 该问题是否被拒答
 └── getMetadata().getUsage()                            → token 用量
```

要点：一次调用可以带多个问题，所有问题都对着同一份输入回答。多问一个问题的成本远小于多调一次模型。

## 文档里的 7 个案例（模块里全部能跑）

启动模块（走本地 Ollama 的 d1-omni:600m，无需 API Key）：

```bash
cd spring-ai2-example-decision-model
./mvnw spring-boot:run
```

### 案例 1：三种问题一次问完 — GET /decision/triage

文档原型：Quick Start 的客服消息分诊（文档原版为英文，模块里已换成等义的中文客服留言）。

```java
DecisionResponse response = decisionModel.call(DecisionPrompt.builder()
    .text("我昨天刚收到新买的耳机，但是左耳完全没有声音，可以退款吗？")
    .question("defect", YesNoQuestion.builder()
        .text("客户是否反映商品存在质量缺陷？")
        .whenYes("商品的一部分无法正常使用")   // 描述"什么算 Yes"
        .whenNo("商品功能正常，消息是关于其他事情的")
        .build())
    .question("topic", ChoiceQuestion.builder()
        .text("这条消息主要关于什么？")
        .option("product", "客户收到的商品存在问题")  // 描述消歧义
        .option("delivery", "订单在哪里，或者什么时候送达")
        .option("account", "登录、地址或支付信息相关")
        .option("other", "以上都不是")
        .build())
    .question("satisfaction", ScoreQuestion.of("客户的满意度如何？", "不满意", "一般", "满意"))
    .build());

boolean defect = response.getYesNo("defect").isYes();
String topic = response.getChoice("topic").getValue();
int satisfaction = response.getScore("satisfaction").getNearestLevel();
```

试试：`curl "http://localhost:8080/decision/triage"`

你会看到（中文输入实测）：

```
是否质量缺陷: 否
消息主题: product
满意度级别: 0（0=不满意, 1=一般, 2=满意）
```

**要点**：

- `whenYes`/`whenNo` 和选项描述不是摆设——它们就是给模型的判断标准。
- 官方建议：描述要写"能消除歧义的程度"（product 选项如果不写描述，会被理解成"任何跟商品有关的消息"）。
- 问题名（"defect"/"topic"/"satisfaction"）就是取答案的 key。
- 注意：选项值（product/delivery）是代码里的英文标识，只有问题文本和输入是中文——key 和 value 分开，代码好处理，模型看得懂。

### 案例 2：概率与阈值 — GET /decision/threshold

文档原型：`isYes(0.9)` —— "自动开维修单这种代价高的动作，只在模型非常确定时执行"。

```java
boolean autoTicket = response.getYesNo("defect").isYes(0.9); // 90% 确定才 true
boolean defaultYes = response.getYesNo("defect").isYes();    // 默认阈值 0.5
```

试试：`curl "http://localhost:8080/decision/threshold"`

你会看到（中文输入实测）：

```
Yes 概率: 0.677
默认阈值 0.5 判定: Yes
高阈值 0.9 判定（自动开维修单）: 转人工
```

**要点**：

- `isYes()` 默认 0.5；`isYes(0.9)` 自己定门槛。
- 阈值是业务决策不是技术参数：动作代价越高，阈值越高。
- 同样的中文消息，这次概率只有 0.677（英文版实测是 0.948）——概率是模型对措辞的敏感反应，这正是文档反复强调"阈值要拿真实样本测"的原因。

### 案例 3：选择题的概率排行 — GET /decision/choice-rank

文档原型：`getRankedOptions()` 拿全部选项的概率排序。

```java
var answer = response.getChoice("topic");
String value = answer.getValue();              // 选中的选项
Double prob = answer.getProbability();         // 选中项的概率
List<String> ranked = answer.getRankedOptions(); // 按概率从高到低
```

试试：`curl "http://localhost:8080/decision/choice-rank"`

你会看到（中文输入实测）：

```
选中: delivery（概率 0.794）
概率排行: [delivery, other, product, account]
```

**要点**：

- 第一名和第二名概率咬得很近 = 消息本身模糊，文档建议这种场景转人工。
- `ChoiceAnswer` 永远会选一个选项——就算四个都不合适它也硬选。真有"都不合适"这种结果，就再配一个 YesNoQuestion 问"这些选项里有合适的吗"。

### 案例 4：结构化字段输入 — GET /decision/fields-input

文档原型：`DecisionInput.fields(customer)` —— 输入带命名字段。

```java
Map<String, Object> customer = new LinkedHashMap<>();  // LinkedHashMap 保序
customer.put("message", "这已经是我第三次遇到订单迟到了。");
customer.put("orders", 12);
customer.put("late_deliveries", 3);

DecisionResponse response = decisionModel.call(
    DecisionInput.fields(customer),
    Map.of("repeated", YesNoQuestion.of("客户是否表示这个问题以前也发生过？")));
```

试试：`curl "http://localhost:8080/decision/fields-input"`

你会看到（中文输入实测）：`是否历史重复问题: 是（Yes 概率 0.824）`

**要点**：

- 字段是带名字传给模型的，模型知道 12 是订单数、3 是迟到次数——比把数字揉进一段文本里清楚得多。
- 字段值支持字符串、数字、布尔、null、Map、List，顶层字段还能放图片。
- 文档建议：只传决策需要的字段，别把整个客户档案倒进去。

### 案例 5：对话输入做守门（guardrail）— GET /decision/guardrail

文档原型：`DecisionInput.conversation(...)` + "最后一条助手回复是否回答了用户的问题"。

```java
DecisionResponse response = decisionModel.call(
    DecisionInput.conversation(List.of(
        new UserMessage("耳机可以退货吗？"),
        new AssistantMessage("您的订单将于本周五送达。"))),
    Map.of("answered", YesNoQuestion.of("最后一条助手回复是否回答了用户的问题？")));
```

试试：`curl "http://localhost:8080/decision/guardrail"`

你会看到（中文输入实测）：`助手是否回答了问题: 是（放行）`

**要点**：

- 这是决策模型最典型的守门用法：在 RAG 链路里检查"引用的文档真的支撑了这个回答吗"、"有没有注入攻击"。
- 对话输入用的就是 ChatModel 的 `Message` 类型，无缝对接。
- 中文实测和英文实测结论相反：英文输入时模型判"否"（拦截，与文档预期一致），中文输入时判"是"（放行）。小模型的跨语言判断一致性有限——关键守门场景上线前，必须拿自己业务的真实语料实测。

### 案例 6：ScoreAnswer 的三种读法 — GET /decision/score-reading

文档核心：同一份答案，三种读法可以完全不同。

```java
satisfaction.getNearestLevel();  // 最可能级别的位置（从 0 起）—— 用来做决策就读它
satisfaction.getValue();         // 概率加权的期望值（可能是小数）—— 1.5 表示模型在两个级别间摇摆
satisfaction.getRoundedLevel();  // 期望值取整 —— 注意它可能落在概率为零的级别上！
```

试试：`curl "http://localhost:8080/decision/score-reading"`

你会看到（中文输入实测）：

```
最可能级别 nearestLevel: 1
加权期望值 getValue: 1.24
期望值取整 roundedLevel: 1
各级别概率: [0.121, 0.522, 0.358]（对应 不满意/一般/满意）
```

**要点**：

- 文档给过一个反直觉例子：概率是 0.5/0/0.5 时，期望值是 1.0，取整后 roundedLevel=1（neutral），但 neutral 的概率是零！
- 做业务决策用 `getNearestLevel()`（最可能的级别），别用取整的期望值。

### 案例 7：调用元数据 — GET /decision/metadata

文档原型：`response.getMetadata()` 有 token 用量（服务商上报的话还有响应 id 和模型名）。

```java
var usage = response.getMetadata().getUsage();
usage.getTotalTokens();
```

试试：`curl "http://localhost:8080/decision/metadata"`

你会看到（中文输入实测）：`token 用量: 输入 44 / 输出 0 / 合计 44`

**要点**：

- 决策调用也按 token 计费，监控成本的方式与聊天模型一致（见 usage-handling 模块笔记）。
- 有趣的细节：输出 0 个 token——决策模型的"输出"是内部的选项打分，不是文本，这正是它便宜的原因。

## 中英文输入实测对比（d1-omni:600m）

同一套案例，英文输入和中文输入的结论不完全一致，实测记录：

| 案例 | 英文输入结果 | 中文输入结果 | 一致性 |
|------|------------|------------|--------|
| triage 缺陷判断 | 是（0.948 附近的高置信场景）| 否 | ❌ 不一致 |
| triage 主题 | product | product | ✅ 一致 |
| choice-rank | product 0.822 | delivery 0.794 | 各自正确（消息本身不同）|
| guardrail | 否（拦截，符合文档预期）| 是（放行）| ❌ 不一致 |
| score-reading | neutral 0.70 | neutral 0.52 | ✅ 级别一致 |

**要点**：

- d1-omni:600m 是 600M 参数的小模型，英文（训练主力语言）判断明显更稳。
- 中文能用，但边界案例的置信度整体偏低（0.677、0.824 vs 英文 0.948）。
- 结论：中文业务要上线，阈值要按中文语料重新标定，守门类场景建议换更大的模型实测。

## 几个 API 的关系，别绕晕

| API | 一句话定位 |
|-----|-----------|
| `DecisionModel.call(String, Map)` | 最短姿势：一段文本 + 问题表 |
| `DecisionModel.call(DecisionInput, Map)` | 输入不限于文本时的姿势 |
| `DecisionPrompt.builder()...build()` | 全功能姿势：多问题+options 一起上 |
| `YesNoQuestion` / `ChoiceQuestion` / `ScoreQuestion` | 三种问题类型 |
| `response.getYesNo(name)` | 取是非题答案（`isYes()` / `isYes(0.9)`）|
| `response.getChoice(name)` | 取选择题答案（`getValue()` / `getRankedOptions()`）|
| `response.getScore(name)` | 取打分题答案（`getNearestLevel()` 等）|
| `response.isRefused(name)` | 该问题是否被服务商拒答 |
| `DecisionInput.text/fields/conversation` | 三种输入类型 |
| `response.getMetadata().getUsage()` | token 用量 |

## 版本与环境注意（本模块专属）

- **Decision API 是 Spring AI 2.1.0 正式版新增**，2.1.0-M1 里没有这套 API——本模块的 `spring-ai.version` 已单独升级为 2.1.0，其他模块仍是 M1。
- 模型用本地 Ollama 的 `d1-omni:600m`（约 850MB），文档要求 Ollama 0.40.4+（本机 0.40.16 可用），`pull-model-strategy: when_missing` 会在启动时自动拉取。

## 踩坑记录

- **双 starter 导致 DecisionModel Bean 冲突**：模块同时引入 openai 和 ollama starter，两边都自动注册了 `DecisionModel`，启动报 "2 were found"。解法：`spring.autoconfigure.exclude` 排除 `OpenAiDecisionAutoConfiguration`。
- **init 配置写错位置**：决策模型名配置在 `spring.ai.ollama.decision.model`，不是 `spring.ai.ollama.init.decision-model`（后者不存在）。写错的结果：模型名落到默认值，自动初始化去拉 Ollama 的默认聊天模型 mistral（本地没有，白下载几百 MB）。
- **init 全局拉取连带效应**：`pull-model-strategy: when_missing` 会把 chat/embedding 的默认模型一起拉。只想初始化决策模型时，要显式关掉 `init.chat.include` 和 `init.embedding.include`。
- **`Map.of` 不保序**：文档提醒问题的回答顺序与提问顺序一致，但 `Map.of(...)` 本身无序——顺序敏感时用 builder 逐个加或用 `LinkedHashMap`。
- **中文输入置信度偏低**：同一案例英文 0.948 vs 中文 0.677，守门案例甚至结论相反。小模型跨语言不稳定，中文场景上线前必须用中文语料重新标定阈值。
- **中文参数 curl 用 `--data-urlencode` 或直接不带参数**：本模块接口全部无参数（内容写在代码里），直接 GET 即可；带中文参数的接口参考其他模块笔记。
