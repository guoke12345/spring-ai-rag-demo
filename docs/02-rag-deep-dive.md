# RAG 检索增强生成

RAG（Retrieval-Augmented Generation）是当前 AI 应用最核心的模式之一。

## 基本原理

LLM 的知识截止于训练数据，且容易产生幻觉（hallucination）。RAG 的核心思路：**在生成答案前，先从外部知识库检索相关信息，作为上下文注入 Prompt**。

## 完整流程

1. **文档加载（Extract）**：从 PDF、Markdown、数据库等来源读取文档
2. **文档分割（Transform）**：用 TokenTextSplitter 将长文档切成小块（chunk），每块通常 300-800 token
3. **向量化（Embed）**：用 EmbeddingModel 将每个 chunk 转为向量（固定维度的浮点数组）
4. **存储（Load）**：将向量存入 VectorStore，同时保留原始文本和元数据
5. **查询时检索**：用户提问 → 同样向量化 → 在 VectorStore 中搜索最相似的 K 个 chunk
6. **增强生成**：将检索到的 chunk 和用户问题拼入 Prompt → 发给 LLM → 生成答案

## 分块策略

分块是 RAG 最关键的超参数之一：

- **Chunk Size（块大小）**：300-800 token 是比较常用的范围。太大检索精度下降，太小丢失上下文
- **Chunk Overlap（重叠）**：50-100 token 的重叠可以避免关键信息被边界切断
- **分隔策略**：按段落、按句子、按固定 token 数。TokenTextSplitter 按 token 数分割比按字符数更合理

## Spring AI 中的 RAG

Spring AI 1.1+ 提供了三个关键抽象：

- **SearchRequest**：封装查询参数（query、topK、similarityThreshold、filterExpression）
- **FilterExpression**：元数据过滤 DSL（如 `category == 'rag' AND year >= 2025`）
- **QuestionAnswerAdvisor**：一行代码实现自动 RAG（查询→检索→增强→生成）

## RAG 的常见问题

- **检索噪音**：检索到不相关的 chunk 反而降低回答质量。改进：提高 similarityThreshold、优化分块策略
- **上下文窗口溢出**：检索太多 chunk 超过模型的上下文限制。改进：合理设置 topK
- **元数据丢失**：检索到的 chunk 缺少背景信息。改进：在 Document 中保留丰富的元数据
