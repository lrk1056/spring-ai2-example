package com.lrk.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文本分块配置类
 */
@Configuration
public class TextSplitterConfig {

    /**
     * 令牌文本切分器
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(100) // 单个文本块的最大 token 数
                .withMinChunkSizeChars(200) // 最小文本块字符数
                .withMaxNumChunks(1000)  // 最大文本框数量。防止对超长文本无限分割，达到此上限后即使还有剩余文本也停止生产新块。
                .withKeepSeparator(true)    // 保留分隔符
                .build();
    }

}
