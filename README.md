# Spring AI RAG Demo

> 基于 Spring AI 1.1 的 RAG（检索增强生成）学习项目。
> 展示 ETL 管线、向量存储抽象、元数据过滤、自动 RAG Advisor 等核心模式。

**技术栈**: Spring Boot 3.4 · Spring AI 1.1.6 · DeepSeek · JDK 21

---

## 架构

```
┌─────────────────────────────────────────────────────────┐
│                       用户请求                           │
│              GET /rag/search?q=什么是RAG                 │
│              GET /rag/filter?q=向量&category=rag         │
│              GET /rag/advisor?q=RAG和微调的区别           │
└────────────┬──────────────┬──────────────┬──────────────┘
             │              │              │
      ┌──────▼──────┐ ┌────▼─────┐ ┌──────▼──────┐
      │ /rag/search │ │/rag/filter│ │ /rag/advisor│
      │ SearchRequest│ │FilterExpr │ │QuestionAnswer│
      │  topK+阈值   │ │元数据过滤  │ │ Advisor(自动)│
      └──────┬──────┘ └────┬─────┘ └──────┬──────┘
             │              │              │
             └──────────────┼──────────────┘
                            │
                  ┌─────────▼─────────┐
                  │    VectorStore    │  ← 接口（可替换实现）
                  │  KeywordVectorStore│  ← 当前：关键词匹配
                  │  (可替换为 PGVector│  ← 未来：真实向量搜索
                  │   /Redis/Chroma)  │
                  └─────────┬─────────┘
                            │
                  ┌─────────▼─────────┐
                  │    ETL Pipeline   │
                  │  docs/*.md        │  ← 启动时自动加载
                  │  → TokenTextSplitter│
                  │  → 11 chunks      │
                  └───────────────────┘
                            │
                  ┌─────────▼─────────┐
                  │    DeepSeek API   │
                  │  (deepseek-chat)  │  ← 兼容 OpenAI 协议
                  └───────────────────┘
```

## 快速启动

```bash
# 1. 设置 API Key
export DEEPSEEK_API_KEY=sk-your-key

# 2. 启动
mvn spring-boot:run

# 3. 观察 ETL 启动日志
# ===== ETL: 01-spring-ai-overview.md → 3 chunks (category=core) =====
# ===== ETL: 02-rag-deep-dive.md → 4 chunks (category=rag) =====
# ===== ETL 完成: 4 个文件 → 11 个 chunk =====
```

> **无需安装 Ollama 或任何向量数据库。** 项目使用内置的 KeywordVectorStore，零外部依赖即可运行。

## API 文档

### 1. 基础搜索 — `/rag/search`

支持 topK 和相似度阈值控制。

```bash
curl "http://localhost:8080/rag/search?q=向量数据库&topK=2&threshold=0.3"
```

```
查询: 向量数据库
TopK: 2, 阈值: 0.3
命中 2 条:
  [vectorstore] 向量数据库是专门设计用来存储和检索向量的数据库系统。与传统的关系型数据库存储...
  [rag] RAG 的核心组件之一是向量存储，它负责存放大规模文档的嵌入向量...
```

### 2. 元数据过滤 — `/rag/filter`

Spring AI 的 FilterExpression DSL，支持 `==`, `>=`, `AND`, `OR`, `NOT`, `IN`。

```bash
# 只看 rag 分类的文档
curl "http://localhost:8080/rag/filter?q=分块策略&category=rag"

# 组合过滤：分类 + 年份
curl "http://localhost:8080/rag/filter?q=嵌入模型&category=advanced&year=2026"
```

### 3. 自动 RAG — `/rag/advisor`

Spring AI 的 QuestionAnswerAdvisor 自动完成 **查询 → 检索 → 拼入上下文 → 发 LLM → 返回答案**，一行配置搞定。

```bash
curl "http://localhost:8080/rag/advisor?q=RAG和模型微调各自的适用场景是什么"
```

```json
{
  "response": "RAG 适合需要实时、准确、可溯源的场景（如客服知识库），
   而微调适合需要模型内化特定风格或领域知识的场景..."
}
```

> 对比：`/rag/search` 只返回检索到的文档片段，`/rag/advisor` 返回 LLM 基于那些片段生成的综合答案。

## 核心设计

### ETL 管线

```
Extract              Transform               Load
docs/*.md  ──────►  TokenTextSplitter  ──►  VectorStore
(TextReader)        (chunk_size=300)        (add/delete/query)
```

启动时自动扫描 `docs/` 目录，每个 .md 文件经过：
1. **Extract**: `TextReader` 读取文件内容
2. **Transform**: `TokenTextSplitter` 按 300 token 切分，自动继承元数据
3. **Load**: 所有 chunk 写入 VectorStore（自动生成 UUID）

### VectorStore 抽象

项目实现了 `VectorStore` 接口的三个核心方法：

| 方法 | 签名 | 用途 |
|------|------|------|
| `add` | `add(List<Document>)` | ETL 入库 |
| `delete` | `delete(List<String>)` / `delete(Filter.Expression)` | 按 ID 或条件删除 |
| `similaritySearch` | `similaritySearch(SearchRequest)` | 语义检索 + FilterExpression 过滤 |

`KeywordVectorStore` 是学习型实现：用关键词滑动窗口匹配替代真实向量相似度，但 API 完全对齐 `VectorStore` 接口。替换为 `SimpleVectorStore`（内存向量）或 `PgVectorStore`（PostgreSQL）只需改一个 `@Bean`。

### FilterExpression 求值器

`KeywordVectorStore` 内建了完整的 FilterExpression DSL 求值引擎，支持：

```
EQ / NE / GT / GTE / LT / LTE / IN / NIN / AND / OR / NOT
```

```java
// 示例: 查找 rag 分类中 year >= 2025 的文档
Filter.Expression filter = new FilterExpressionTextParser()
    .parse("category == 'rag' AND year >= 2025");

SearchRequest request = SearchRequest.builder()
    .query("分块策略")
    .filterExpression(filter)
    .build();
```

## 设计决策

| 决策 | 理由 |
|------|------|
| **KeywordVectorStore 而非 SimpleVectorStore** | 避开 Ollama 安装依赖（~1GB），让项目零门槛启动。关键词匹配在 4 个文档、11 个 chunk 的规模下足够用。 |
| **文件驱动 ETL** | 从 `docs/` 自动加载，无需外部数据源。知识库内容和代码放在一起，方便版本管理。 |
| **FilterExpression 求值器自己写** | 加深对 Spring AI Filter DSL 的理解。生产环境推荐用 `SimpleVectorStore` 自带的 Filter 实现。 |
| **DeepSeek 而非 OpenAI** | 国内网络友好，API 兼容 OpenAI 协议，零代码切换。 |
| **分离 `/rag/search` 和 `/rag/advisor`** | 分别展示「检索」和「检索+生成」两个阶段，方便面试时讲清楚 RAG 的每一步。 |

## 知识库

`docs/` 目录下 4 篇 Spring AI 文档，按分类标注元数据：

| 文件 | 分类 | 内容 |
|------|------|------|
| `01-spring-ai-overview.md` | core | Spring AI 概览、核心抽象 |
| `02-rag-deep-dive.md` | rag | RAG 深入：检索策略、chunk 设计 |
| `03-vectorstore.md` | vectorstore | 向量存储对比、索引类型 |
| `04-advanced-features.md` | advanced | Function Calling、多模态等 |

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.4.4 | |
| Spring AI | 1.1.6 | spring-ai-openai-spring-boot-starter |
| DeepSeek | deepseek-chat | 兼容 OpenAI 协议 |
| JDK | 21 | |
| Maven | 3.8+ | |

## 已知局限 & 路线图

- [x] ETL 管线（文件驱动）
- [x] KeywordVectorStore + FilterExpression 求值器
- [x] `/rag/search` + `/rag/filter` + `/rag/advisor` 三个端点
- [ ] 替换为真实向量嵌入（Ollama nomic-embed-text 或在线 API）
- [ ] 流式输出（SSE / StreamingResponse）
- [ ] 对话记忆（MessageChatMemoryAdvisor）
- [ ] Function Calling 集成
- [ ] 单元测试覆盖 ETL + Controller

## License

MIT — 学习项目，随意使用。
