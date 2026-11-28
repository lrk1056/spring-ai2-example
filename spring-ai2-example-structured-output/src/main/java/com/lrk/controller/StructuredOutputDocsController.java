package com.lrk.controller;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured Output 四篇官方文档的核心案例演示：
 * <p>
 * 1. 主文档（/api/structured-output.html）     → responseEntity 拿"对象 + 元数据"
 * 2. Validation（/validation.html）            → validateSchema() 自纠错重试
 * 3. Native（/native.html）                    → useProviderStructuredOutput() 请求侧约束
 * 4. Converters（/converters.html）            → 三个内置转换器 + 自定义宽松转换器
 */
@RestController
@RequestMapping("/structured-output/docs")
public class StructuredOutputDocsController {

    @Resource
    private ChatClient openAiChatClient;

    // ==================== 目标类型定义 ====================

    /**
     * 文档的标准示例类型：演员的电影作品
     *
     * @JsonPropertyOrder 控制生成 JSON Schema 中的属性顺序（converters 文档）
     */
    @JsonPropertyOrder({"actor", "movies"})
    public record ActorsFilms(String actor, List<String> movies) {
    }

    /**
     * native 文档的替代方案：把 List 包进容器 record
     * 原因：OpenAI 原生结构化输出不支持顶层是数组
     */
    public record FilmographyList(List<ActorsFilms> films) {
    }

    // ==================== 主文档案例 ====================

    /**
     * 案例 1：responseEntity 对象和元数据一起拿
     * 对应主文档 "Getting the Full Response"：
     * .entity() 只返回解析后的对象；还想要 token 用量等元数据时用 .responseEntity()。
     */
    @GetMapping("/response-entity")
    public String responseEntity() {
        ResponseEntity<ChatResponse, ActorsFilms> result = openAiChatClient.prompt()
                .user("生成一个随机演员的电影记录.")
                .call()
                .responseEntity(ActorsFilms.class);

        ActorsFilms films = result.entity();      // 结构化对象
        ChatResponse raw = result.response();     // 原始响应（含元数据）
        long totalTokens = raw.getMetadata().getUsage().getTotalTokens();

        return "演员: " + films.actor()
                + "\n电影数: " + films.movies().size()
                + "\n本次消耗 tokens: " + totalTokens;
    }

    // ==================== Validation 文档案例 ====================

    /**
     * 案例 2：validateSchema() 校验失败时自动纠错重试
     *
     * 对应 validation 文档 "How the Self-Correcting Loop Works"：
     * 校验失败时把具体错误塞回 prompt 重发（默认最多 3 次），不是盲目重试。
     */
    @GetMapping("/validate-schema")
    public String validateSchema() {
        ActorsFilms films = openAiChatClient.prompt()
                .user("生成一个随机演员的电影记录.")
                .call()
                // 开启自纠错：响应不符合 schema 就带着错误信息重试
                .entity(ActorsFilms.class, ChatClient.EntityParamSpec::validateSchema);
        return "校验通过 → 演员: " + films.actor() + "，电影: " + films.movies();
    }

    // ==================== Native 文档案例 ====================

    /**
     * 案例 3：useProviderStructuredOutput() ：请求侧约束
     *
     * 对应 native 文档：schema 作为 API 级字段发给服务商，由服务商运行时强制约束，
     * 不再靠 prompt 里塞格式说明（省 token、更可靠）。
     * 注意：要求模型支持原生结构化输出；不支持时该开关被静默忽略，回退到 prompt 方式。
     */
    @GetMapping("/provider-native")
    public String providerNative() {
        ActorsFilms films = openAiChatClient.prompt()
                .user("生成一个随机演员的电影记录.")
                .call()
                .entity(ActorsFilms.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);
        return "原生约束生效 → 演员: " + films.actor() + "，电影数: " + films.movies().size();
    }

    /**
     * 案例 4：两个开关组合 + 顶层 List 的容器 record 方案
     *
     * 对应 native 文档 "OpenAI: No Top-Level Array Support" 和 "Combining Both"：
     * - 直接要 List<T> 且开原生约束，OpenAI 会报 API 错误（不支持顶层数组）
     * - 解法：把 List 包进容器 record 再开原生约束
     * - validateSchema() 兜底残留的格式漂移
     */
    @GetMapping("/native-with-validation")
    public String nativeWithValidation() {
        FilmographyList result = openAiChatClient.prompt()
                .user("生成汤姆汉克斯和比利墨瑞的电影记录.")
                .call()
                // 容器 record 规避"顶层数组"限制；两个开关叠加：请求侧约束 + 响应侧校验
                .entity(FilmographyList.class, spec -> spec.useProviderStructuredOutput().validateSchema());
        return "组合拳成功，共拿到 " + result.films().size() + " 位演员的作品列表";
    }

    // ==================== Converters 文档案例 ====================

    /**
     * 案例 5：MapOutputConverter —— 低阶 ChatModel 直调
     * 对应 converters 文档 "Map Output Converter"（低阶用法）：
     * 手动把 converter.getFormat() 塞进模板的 {format} 占位符，
     * 拿到文本后手动 convert()。想看清两阶段流程时用这个姿势。
     */
    @GetMapping("/map-converter-manual")
    public String mapConverterManual() {
        MapOutputConverter mapOutputConverter = new MapOutputConverter();

        // 阶段一：getFormat() 生成的格式指令塞进 prompt
        String format = mapOutputConverter.getFormat();
        String template = """
                Provide me a List of {subject}
                {format}
                """;
        Prompt prompt = PromptTemplate.builder()
                .template(template)
                .variables(Map.of(
                        "subject", "an array of numbers from 1 to 9 under they key name 'numbers'",
                        "format", format))
                .build()
                .create();

        // 实测 qwen-plus 会无视格式指令输出解释文字导致解析失败，
        // 所以补一条 system 强约束——这也是文档说 converter 是"尽力而为"的原因
        Generation generation = openAiChatClient.prompt(prompt)
                .system("严格遵守格式指令：只输出 JSON 本身，禁止输出任何解释、前缀或代码块标记。")
                .call()
                .chatResponse()
                .getResult();

        // 阶段二：convert() 把文本解析成 Map
        Map<String, Object> result = mapOutputConverter.convert(generation.getOutput().getText());
        return "解析结果: " + result;
    }

    /**
     * 案例 6：ListOutputConverter：非JSON的逗号分隔列表
     *
     * 对应 converters 文档 "List Output Converter"（高阶用法）：.entity() 直接传 converter 实例，适合"给我列 5 个xxx"这种轻量场景。
     */
    @GetMapping("/list-converter")
    public String listConverter() {
        List<String> flavors = openAiChatClient.prompt()
                .user(u -> u.text("List five {subject}")
                        .param("subject", "ice cream flavors"))
                .call()
                .entity(new ListOutputConverter(new DefaultConversionService()));
        return "冰淇淋口味: " + flavors;
    }

    /**
     * 案例 7：自定义宽松转换器：专治 markdown 代码围栏包裹的 JSON
     *
     * 对应 converters 文档 "Custom Converters"：
     * BeanOutputConverter 很严格，模型回复 "Here's ... ```json {...```" 会直接解析炸。
     * 自定义 converter 先剥围栏再委托默认解析器；getJsonSchema() 委托回去后两个可靠性开关照常可用。
     */
    static class LenientJsonOutputConverter<T> implements StructuredOutputConverter<T> {

        // 匹配 ```json ... ``` 围栏
        private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

        private final BeanOutputConverter<T> delegate;

        LenientJsonOutputConverter(Class<T> targetType) {
            this.delegate = new BeanOutputConverter<>(targetType);
        }

        @Override
        public String getFormat() {
            return delegate.getFormat();
        }

        @Override
        public String getJsonSchema() {
            // 委托回去，validateSchema()/useProviderStructuredOutput() 才能继续工作
            return delegate.getJsonSchema();
        }

        @Override
        public T convert(String source) {
            Matcher matcher = FENCE.matcher(source);
            String json = matcher.find() ? matcher.group(1).trim() : source.trim();
            return delegate.convert(json);
        }
    }

    @GetMapping("/lenient-converter")
    public String lenientConverter() {
        // 故意在 user 消息里要求模型"啰嗦一点"，增加围栏输出概率，验证宽松解析
        ActorsFilms films = openAiChatClient.prompt()
                .user("Generate the filmography for a random actor. 先用一句话介绍这个演员，然后用 ```json 代码块输出作品列表。")
                .call()
                .entity(new LenientJsonOutputConverter<>(ActorsFilms.class));
        return "宽松解析成功 → 演员: " + films.actor() + "，电影数: " + films.movies().size();
    }

}
