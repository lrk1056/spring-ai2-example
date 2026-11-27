package com.lrk.controller;

import com.lrk.util.DocumentParseUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
public class MyRagController {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private VectorStore vectorStore; // 向量存储

    @Autowired
    private TokenTextSplitter tokenTextSplitter; // 文本分块器

    @Autowired
    private DocumentParseUtil documentParseUtil; // 文档解析工具

    /**
     * 加载资源目录中的 docs 下的文件，切分保存为向量
     */
    @PostMapping("/addDocs")
    public String addDocs() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:docs/*");

        for (Resource resource : resources) {
            if (!resource.isFile() || resource.getFile().isDirectory()) {
                continue;
            }
            String filePath = resource.getFile().getAbsolutePath();
            String fileName = resource.getFilename();
            addChunkedDocuments(filePath, fileName);
        }
        return "OK";
    }

    /**
     * 解析后按 token 切分再入库，避免单条 Document 超过嵌入模型输入上限
     */
    private void addChunkedDocuments(String filePath, String label) {
        List<Document> raw = documentParseUtil.parse(filePath);
        List<Document> chunks = tokenTextSplitter.apply(raw);
        vectorStore.add(chunks);
        System.out.println(label + ",向量存储添加成功！（共 " + chunks.size() + " 块）");
    }

    /**
     * 向量查询
     */
    @GetMapping("/similarityAsk")
    public String similarityAsk(String question) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(3)
                        .build());
        System.out.println(docs);
        return "OK";
    }

    /**
     * RAG 回答
     */
    @GetMapping("/ask_rag")
    public String ask_rag(String question) {
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(3) // 搜索结果数量
                        .similarityThreshold(0.7) // 相似度阈值 0-1 值越大要求越严格 过滤低相关性的结果，提高 RAG 回答质量
                        .build())
                .build();
        String result = chatClient.prompt()
                .advisors(qaAdvisor)
                .user(question)
                .call()
                .content();
        System.out.println(result);
        return "ok";
    }

}
