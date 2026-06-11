# Spring AI 概述

Spring AI 是 Spring 官方推出的 Java AI 框架，对标 Python 的 LangChain 和 LlamaIndex。

## 核心设计理念

Spring AI 的设计哲学是"Spring 风格"的 AI 集成。如果你用过 Spring MVC、Spring Data，那 Spring AI 的上手曲线会很平滑。

## 核心组件

- **ChatClient**：统一的聊天接口，支持 OpenAI、DeepSeek、Ollama、Anthropic 等多种后端
- **EmbeddingModel**：文本嵌入模型接口，将文本转为向量
- **VectorStore**：向量数据库抽象，支持 SimpleVectorStore、Chroma、PgVector、Pinecone 等
- **Document**：文档模型，包含文本内容和元数据

## 支持的模型提供商

Spring AI 通过统一的抽象层支持多种模型提供商，切换提供商只需改配置：
- OpenAI 系列（GPT-4、GPT-4o）
- DeepSeek（通过 OpenAI 兼容协议）
- Ollama（本地部署，支持 Llama、Qwen 等开源模型）
- Anthropic Claude
- Google Gemini

## 与 Spring 生态的集成

Spring AI 深度集成了 Spring Boot 的自动配置、Spring 的依赖注入、以及 Actuator 的健康检查。这意味着你可以用熟悉的 Spring 编程模型来构建 AI 应用。
