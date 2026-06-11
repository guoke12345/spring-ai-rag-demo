# Spring AI 高级特性

## ChatClient 高级用法

### 系统提示词（System Prompt）

```java
chatClient.prompt()
    .system("你是一个专业的 Java 技术顾问，回答要简洁准确")
    .user("Spring AI 怎么集成 DeepSeek？")
    .call()
    .content();
```

### 流式输出（Streaming）

```java
Flux<String> stream = chatClient.prompt()
    .user("讲个笑话")
    .stream()
    .content();
```

流式输出用 SSE（Server-Sent Events）协议，前端可以实时逐字显示。

### Advisors 链

Advisor 是 Spring AI 的拦截器模式，可以在请求前后做处理：

```java
chatClient.prompt()
    .advisors(
        new QuestionAnswerAdvisor(vectorStore),  // RAG
        new SimpleLoggerAdvisor()                // 日志
    )
    .user(query)
    .call()
    .content();
```

## Function Calling（工具调用）

让 LLM 能调用你的 Java 方法。定义 `@Tool` 注解的方法：

```java
@Tool(description = "获取指定城市的天气")
public String getWeather(String city) {
    return city + "今天晴天，25°C";
}
```

LLM 会自动判断什么时候需要调用这些函数，并提取参数。

## Multimodal（多模态）

Spring AI 支持图文混合输入：

```java
chatClient.prompt()
    .user(userSpec -> userSpec
        .text("这张图里有什么？")
        .media(new UrlResource("https://..."))
    )
    .call()
    .content();
```

## 对话记忆（ChatMemory）

用 ChatMemory 实现多轮对话：

```java
var memory = new InMemoryChatMemory();
var advisor = new MessageChatMemoryAdvisor(memory);

chatClient.prompt()
    .advisors(advisor)  // 自动注入历史消息
    .user("我叫张三")
    .call()
    .content();
```

下次对话时，LLM 就能记住"张三"这个名字。

## 输出解析（Output Parsers）

强制 LLM 输出特定格式：

- **BeanOutputParser**：输出为 Java Bean（JSON 自动映射）
- **ListOutputParser**：输出为逗号分隔列表
- **自定义 Parser**：实现 OutputParser 接口

## 企业级特性

- **Spring Boot Auto Configuration**：自动装配，零代码接入
- **Actuator 健康检查**：监控 AI 服务的可用性
- **Micrometer 指标**：Token 消耗、延迟、调用次数
- **RetryTemplate**：自动重试失败的 API 调用
