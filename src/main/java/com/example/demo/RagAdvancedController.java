package com.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class RagAdvancedController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagAdvancedController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    /**
     * 演示1: SearchRequest — 高级搜索参数
     * GET /rag/search?q=向量数据库&topK=2&threshold=0.3
     */
    @GetMapping("/rag/search")
    public String search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "3") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {

        // SearchRequest 封装了检索的所有参数
        var request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        if (results.isEmpty()) {
            return "未找到匹配的文档。（相似度阈值: " + threshold + "）";
        }

        return "查询: " + query + "\n"
                + "TopK: " + topK + ", 阈值: " + threshold + "\n"
                + "命中 " + results.size() + " 条:\n"
                + results.stream()
                    .map(d -> "  [" + d.getMetadata().get("category") + "] " + truncate(d.getText(), 100))
                    .collect(Collectors.joining("\n"));
    }

    /**
     * 演示2: FilterExpression — 元数据过滤
     * GET /rag/filter?q=分块&category=rag&year=2025
     *
     * FilterExpression DSL 语法:
     *   category == 'rag' AND year >= 2025
     */
    @GetMapping("/rag/filter")
    public String filter(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "0") int year) {

        // 构建 FilterExpression
        var parser = new FilterExpressionTextParser();
        Filter.Expression filter = null;

        if (!category.isBlank() && year > 0) {
            filter = parser.parse("category == '" + category + "' AND year >= " + year);
        } else if (!category.isBlank()) {
            filter = parser.parse("category == '" + category + "'");
        } else if (year > 0) {
            filter = parser.parse("year >= " + year);
        }

        var request = SearchRequest.builder()
                .query(query)
                .topK(3)
                .filterExpression(filter)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        return "查询: " + query + "\n"
                + "过滤: " + (filter != null ? filter.toString() : "无") + "\n"
                + "命中 " + results.size() + " 条:\n"
                + results.stream()
                    .map(d -> "  [" + d.getMetadata().get("category") + "|" + d.getMetadata().get("year") + "] "
                            + truncate(d.getText(), 100))
                    .collect(Collectors.joining("\n"));
    }

    /**
     * 演示3: QuestionAnswerAdvisor — 自动 RAG
     * GET /rag/advisor?q=什么是RAG
     *
     * Advisor 自动完成: 查询 → 检索 → 拼入上下文 → 发 LLM → 返回答案
     * 比自己手写检索+拼接逻辑简洁太多
     */
    @GetMapping("/rag/advisor")
    public String advisor(@RequestParam("q") String query) {

        // 构建 RAG Advisor（一行配置！）
        var advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .build())
                .build();

        // Advisor 自动注入 RAG 上下文，无需手动拼接 prompt
        return chatClient.prompt()
                .advisors(advisor)
                .user(query)
                .call()
                .content();
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
