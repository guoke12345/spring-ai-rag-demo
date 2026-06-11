package com.example.demo;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于关键词匹配的 VectorStore 实现。
 * API 完全对齐 {@link VectorStore} 接口——支持 SearchRequest、FilterExpression。
 * 仅用于学习 Spring AI RAG 概念，等 Ollama 装好后替换为 SimpleVectorStore。
 */
public class KeywordVectorStore implements VectorStore {

    private final Map<String, Document> store = new ConcurrentHashMap<>();

    @Override
    public void add(List<Document> documents) {
        for (Document doc : documents) {
            String id = doc.getId();
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            store.put(id, doc);
        }
        System.out.println("[KeywordVectorStore] 写入 " + documents.size() + " 条文档，当前总量: " + store.size());
    }

    @Override
    public void delete(List<String> idList) {
        idList.forEach(store::remove);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        // 简化实现：按 filter 表达式匹配后删除
        List<String> toDelete = store.entrySet().stream()
                .filter(e -> evaluateFilter(e.getValue(), filterExpression))
                .map(Map.Entry::getKey)
                .toList();
        toDelete.forEach(store::remove);
        System.out.println("[KeywordVectorStore] 按 Filter 删除 " + toDelete.size() + " 条");
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String query = request.getQuery();
        int topK = request.getTopK() > 0 ? request.getTopK() : 4;
        Filter.Expression filter = request.getFilterExpression();

        return store.values().stream()
                // 1. 先按 FilterExpression 过滤
                .filter(doc -> filter == null || evaluateFilter(doc, filter))
                // 2. 关键词打分
                .map(doc -> new AbstractMap.SimpleEntry<>(doc, keywordScore(query, doc.getText())))
                // 3. 相似度阈值过滤（normalize 到 0~1 范围）
                .filter(e -> e.getValue() / 10.0 >= request.getSimilarityThreshold())
                // 4. 按分数降序
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public List<Document> similaritySearch(String query) {
        return similaritySearch(SearchRequest.builder().query(query).topK(4).build());
    }

    // ========== 关键词打分（模拟向量相似度） ==========

    private double keywordScore(String query, String text) {
        String lq = query.toLowerCase();
        String lt = text.toLowerCase();
        if (lt.contains(lq)) return 10.0;

        double score = 0;
        // 2~4 字中文 sliding window
        for (int len = 4; len >= 2; len--) {
            for (int i = 0; i <= lq.length() - len; i++) {
                if (lt.contains(lq.substring(i, i + len))) {
                    score += len * 0.5;
                }
            }
        }
        return score;
    }

    // ========== FilterExpression 求值 ==========

    private boolean evaluateFilter(Document doc, Filter.Expression expr) {
        Filter.ExpressionType type = expr.type();
        return switch (type) {
            case EQ -> getMeta(doc, expr.left()).equals(getValue(expr.right()));
            case NE -> !getMeta(doc, expr.left()).equals(getValue(expr.right()));
            case GT, GTE, LT, LTE -> compareNumbers(doc, expr);
            case IN -> inList(doc, expr);
            case NIN -> !inList(doc, expr);
            case AND -> evaluateFilter(doc, (Filter.Expression) expr.left())
                    && evaluateFilter(doc, (Filter.Expression) expr.right());
            case OR -> evaluateFilter(doc, (Filter.Expression) expr.left())
                    || evaluateFilter(doc, (Filter.Expression) expr.right());
            case NOT -> !evaluateFilter(doc, (Filter.Expression) expr.left());
            default -> true;
        };
    }

    private String getMeta(Document doc, Filter.Operand operand) {
        if (operand instanceof Filter.Key key) {
            return String.valueOf(doc.getMetadata().getOrDefault(key.key(), ""));
        }
        return "";
    }

    private String getValue(Filter.Operand operand) {
        if (operand instanceof Filter.Value val) {
            return String.valueOf(val.value());
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private boolean inList(Document doc, Filter.Expression expr) {
        String metaVal = getMeta(doc, expr.left());
        if (expr.right() instanceof Filter.Value val && val.value() instanceof List<?> list) {
            return list.stream().anyMatch(item -> String.valueOf(item).equals(metaVal));
        }
        return false;
    }

    private boolean compareNumbers(Document doc, Filter.Expression expr) {
        try {
            double metaNum = Double.parseDouble(getMeta(doc, expr.left()));
            double targetNum = Double.parseDouble(getValue(expr.right()));
            return switch (expr.type()) {
                case GT -> metaNum > targetNum;
                case GTE -> metaNum >= targetNum;
                case LT -> metaNum < targetNum;
                case LTE -> metaNum <= targetNum;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
