package com.lrk.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class RedisEmController {

    @Autowired
    private VectorStore vectorStore;

    @RequestMapping("/addDoc")
    public String addDoc() {
        List<Document> documents = List.of(
                new Document("学java上B站", Map.of("meta1", "meta1")),
                new Document("B站是个学Java的好地方"),
                new Document("我喜欢打篮球", Map.of("meta2", "meta2"))
        );

        // 添加文档到向量存储
        vectorStore.add(documents);
        return "ok";
    }

    @RequestMapping("/query")
    public String query() {
        // 查询向量存储
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Java")
                        .topK(3)
                        .build()
        );
        System.out.println(docs);
        return "ok";
    }


}
