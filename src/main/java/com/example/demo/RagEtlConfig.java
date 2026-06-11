package com.example.demo;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.util.*;

/**
 * ETL 管线配置：从 docs/ 目录加载 Markdown 文档 → 分割 → 存入 VectorStore。
 * 
 * 管线阶段:  Extract          Transform           Load
 *           docs/*.md  →  TokenTextSplitter  →  VectorStore
 */
@Configuration
public class RagEtlConfig {

    private static final String DOCS_DIR = "docs/";
    private static final int CHUNK_SIZE = 300;   // token 数（中文约等于字符数）

    @Bean
    public VectorStore vectorStore() {
        var vs = new KeywordVectorStore();
        var splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE)
                .withMinChunkSizeChars(50)
                .withKeepSeparator(true)
                .build();

        File dir = new File(DOCS_DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".md"));
        if (files == null || files.length == 0) {
            System.out.println("===== ETL: docs/ 目录为空或无 .md 文件 =====");
            return vs;
        }

        Arrays.sort(files); // 按文件名排序，保证加载顺序一致

        int totalFiles = 0;
        int totalChunks = 0;

        for (File file : files) {
            // === Extract：从文件读取 ===
            var reader = new TextReader(new FileSystemResource(file.getAbsolutePath()));
            List<Document> rawDocs = reader.get();
            
            // 根据文件名推断 category，标注在 chunk 上
            String category = inferCategory(file.getName());
            
            // 给原始文档加元数据（分割后会自动继承到 chunk）
            for (Document doc : rawDocs) {
                doc.getMetadata().put("category", category);
                doc.getMetadata().put("year", 2026);
                doc.getMetadata().put("source", file.getName());
                doc.getMetadata().put("source_path", file.getAbsolutePath());
            }

            // === Transform：分割 ===
            List<Document> chunks = splitter.apply(rawDocs);
            totalChunks += chunks.size();
            totalFiles++;

            System.out.printf("===== ETL: %s → %d chunks (category=%s) =====%n",
                    file.getName(), chunks.size(), category);

            for (int i = 0; i < Math.min(3, chunks.size()); i++) {
                Document c = chunks.get(i);
                String preview = c.getText().length() > 80
                        ? c.getText().substring(0, 80).replace("\n", " ") + "..."
                        : c.getText().replace("\n", " ");
                System.out.printf("  [chunk %d] (%d 字符) %s%n", i, c.getText().length(), preview);
            }
            if (chunks.size() > 3) {
                System.out.printf("  ... 共 %d 个 chunk%n", chunks.size());
            }

            // === Load：入库 ===
            vs.add(chunks);
        }

        System.out.printf("===== ETL 完成: %d 个文件 → %d 个 chunk =====%n",
                totalFiles, totalChunks);
        return vs;
    }

    /**
     * 从文件名推断文档分类。用于 FilterExpression 过滤演示。
     */
    private static final Map<String, String> CATEGORY_MAP = Map.of(
            "01-spring-ai-overview", "core",
            "02-rag-deep-dive", "rag",
            "03-vectorstore", "vectorstore",
            "04-advanced-features", "advanced"
    );

    private String inferCategory(String filename) {
        // 去掉 .md 后缀
        String base = filename.replaceAll("\\.md$", "");
        return CATEGORY_MAP.getOrDefault(base, "other");
    }
}
