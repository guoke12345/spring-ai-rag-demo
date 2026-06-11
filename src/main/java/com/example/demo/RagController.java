package com.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class RagController {

    private final ChatClient chatClient;
    private final List<Document> knowledgeBase;

    public RagController(ChatClient.Builder chatClientBuilder, List<Document> knowledgeBase) {
        this.chatClient = chatClientBuilder.build();
        this.knowledgeBase = knowledgeBase;
    }

    @GetMapping("/rag")
    public String rag(@RequestParam String question) {
        // 1. 检索：从知识库中找出最相关的 chunk（关键词匹配，以后换 VectorStore.similaritySearch）
        List<Document> retrieved = retrieve(question, 3);

        if (retrieved.isEmpty()) {
            return "未找到相关内容。";
        }

        // 2. 增强：把检索到的 chunk 拼成上下文
        String context = retrieved.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // 3. 生成：拼接提示词，让 LLM 结合上下文回答
        String prompt = """
                你是一个知识助手。请根据以下参考资料回答问题。
                如果参考资料中没有相关信息，请如实说"参考资料中未提及"。

                参考资料：
                %s

                问题：%s

                回答：""".formatted(context, question);

        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 日志：打印检索到的 chunks
        System.out.println("===== RAG 查询 =====");
        System.out.println("问题: " + question);
        System.out.println("检索到 " + retrieved.size() + " 个 chunk:");
        for (int i = 0; i < retrieved.size(); i++) {
            String preview = retrieved.get(i).getText();
            if (preview.length() > 80) preview = preview.substring(0, 80) + "...";
            System.out.println("  [" + i + "] " + preview);
        }

        return answer;
    }

    /**
     * 简易关键词检索（占位实现，未来替换为 VectorStore.similaritySearch）
     */
    private List<Document> retrieve(String query, int topK) {
        // 精确匹配优先，包含匹配次之
        return knowledgeBase.stream()
                .map(doc -> new AbstractMap.SimpleEntry<>(doc, score(query, doc.getText())))
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private double score(String query, String text) {
        String lowerQuery = query.toLowerCase();
        String lowerText = text.toLowerCase();

        // 完全匹配（整个 query 短语命中）
        if (lowerText.contains(lowerQuery)) return 10.0;

        // 中文：用 2-gram 滑动窗口做模糊匹配
        double score = 0;
        // 英文：按空格分词
        String[] words = lowerQuery.split("\\s+");
        if (words.length > 1) {
            for (String word : words) {
                if (word.length() >= 2 && lowerText.contains(word)) {
                    score += 1.5;
                }
            }
        }
        // 中文：用 2-4 字滑动窗口
        for (int len = 4; len >= 2; len--) {
            for (int i = 0; i <= lowerQuery.length() - len; i++) {
                String gram = lowerQuery.substring(i, i + len);
                if (lowerText.contains(gram)) {
                    score += len * 0.5;  // 越长权重越高
                }
            }
        }
        return score;
    }
}
