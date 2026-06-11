# VectorStore 向量数据库

## 什么是向量

向量是一组固定维度的浮点数，用来表示文本的语义信息。语义相似的文本，它们的向量在空间中距离更近。

例如：
- "猫是一种动物" 和 "狗是一种宠物" → 向量距离近
- "猫是一种动物" 和 "今天天气很好" → 向量距离远

## 向量数据库的作用

传统数据库：精确匹配（`WHERE name = 'xxx'`）
向量数据库：语义相似度搜索（"找出和这段文本最相似的 K 条内容"）

## 相似度算法

- **余弦相似度（Cosine Similarity）**：最常用，范围 -1 到 1，1 表示完全相同
- **欧氏距离（Euclidean Distance）**：距离越近越相似
- **内积（Dot Product）**：值越大越相似

## Spring AI 中的 VectorStore

Spring AI 提供了一个 `VectorStore` 接口，屏蔽了不同向量数据库的差异：

```java
public interface VectorStore {
    void add(List<Document> documents);
    void delete(List<String> idList);
    List<Document> similaritySearch(SearchRequest request);
}
```

## 支持的后端

### SimpleVectorStore（内存）
- 适合 Demo 和开发环境
- 数据存在 JVM 内存中，重启丢失
- 不支持持久化

### Chroma（开源）
- 开源向量数据库，Python 实现
- 支持嵌入函数内置计算
- 适合中小规模应用

### PgVector（PostgreSQL 扩展）
- 利用现有 PostgreSQL 基础设施
- 支持混合查询（SQL + 向量搜索）
- 适合已有 PostgreSQL 的团队

### Redis Stack
- 基于 Redis 的向量搜索
- 低延迟，适合实时场景

### 云服务
- Pinecone：全托管向量数据库
- Weaviate：开源向量数据库，有云服务版本
- Milvus：高性能向量数据库

## 元数据过滤

实际应用中，不仅需要语义搜索，还需要精确过滤。Spring AI 的 FilterExpression 支持：

- `category == 'rag' AND year >= 2025`
- `source IN ['official', 'community']`
- `NOT (status == 'deprecated')`

元数据过滤 + 向量相似度的组合查询，是生产级 RAG 的标准做法。
