package com.lrk.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class VectorStoreConfig {

    /**
     * 自定义分批策略
     * 很多大模型服务商（如阿里云百炼、SiliconFlow 等）或兼容 OpenAI 协议的第三方接口，为保障服务稳定性，对 Embedding 接口有严格的单次请求条数限制。
     * 例如，阿里云的 text-embedding-v3 / v4 模型，单次最多只能处理 10 条文本；部分其他模型或代理网关可能限制为 20 条
     * 当使用 TokenTextSplitter 切分完一篇长文档后，可能会产生几十甚至上百个 Document。
     * 直接调用 vectorStore.add(allDocuments)，Spring AI 默认会尝试将它们一次性打包发送，从而触发 400 错误
     * 最稳妥且通用的做法是调用 vectorStore.add() 时，手动将文档列表切分成小批次（例如每批 8 条或 10 条），循环入库
     * 比如：
     *  - Guava 的 Lists.partition
     *  - Hutool 的 ListUtil.split
     *  - Spring AI 自带的 BatchingStrategy 批次策略，强制框架在调用 Embedding 模型前按固定条数（而非 Token 数）进行分批（推荐）
     */
    @Bean
    public BatchingStrategy customBatchingStrategy() {
        return documents -> {
            log.info("自定义分批策略调用，包含 {} 份文档", documents.size());
            List<List<Document>> batches = new ArrayList<>();
            // 强制按每 10 条进行分批，无视 Token 数量
            for (int i = 0; i < documents.size(); i += 10) {
                batches.add(documents.subList(i, Math.min(i + 10, documents.size())));
            }
            return batches;
        };
    }

}