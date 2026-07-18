# 测试需求：Spring AI RAG Demo

项目路径：/home/qibao/projects/spring-ai-rag-demo/
测试目录：src/test/java/com/example/demo/

## 要写的测试文件

请创建以下测试文件：

### 1. KeywordVectorStoreTest.java
测试 KeywordVectorStore 的核心方法：
- add() → 写入文档后 store 不为空
- similaritySearch() → 用 "向量数据库" 查询应命中 docs/03-vectorstore.md 的内容
- similaritySearch with FilterExpression → category == 'rag' 过滤
- similaritySearch with threshold → 设置高阈值应返回空
- delete by ID → 删除后搜索不到
- delete by FilterExpression → 按分类删除

### 2. RagEtlConfigTest.java
测试 ETL 管线：
- vectorStore bean 创建后不为空
- 4 个 docs/*.md 文件都被加载（验证 store 中有 core/rag/vectorstore/advanced 四个分类的 chunk）
- chunk 数量 >= 8（4 个文件分割后至少 8 个 chunk）

### 3. RagAdvancedControllerTest.java
Spring Boot 集成测试（@SpringBootTest + @AutoConfigureMockMvc）：
- GET /rag/search?q=向量数据库 → 返回包含 "向量数据库" 的内容
- GET /rag/filter?q=分块&category=rag → 返回的结果中无其他分类
- GET /rag/advisor?q=什么是RAG → 返回非空响应（需要 DEEPSEEK_API_KEY 环境变量）
- 如果 DEEPSEEK_API_KEY 不可用，跳过 advisor 测试（@Disabled）

## 注意事项

- 使用 JUnit 5 + Spring Boot Test
- KeywordVectorStore 测试是纯单元测试，不需要 Spring 上下文
- RagEtlConfigTest 需要 @SpringBootTest 或 @ContextConfiguration
- 测试应该能通过 `mvn test` 运行
- Reader/TokenTextSplitter 来自 spring-ai-commons
- VectorStore 接口来自 spring-ai-vector-store
- 已有一个空的 src/test/java/com/example/demo/RagDemoApplicationTests.java，可以覆盖或删除
- 依赖已在 pom.xml 中，不需要修改

## 验证

完成后运行 `mvn test -q 2>&1 | tail -20` 确保所有测试通过。
