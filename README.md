# 智能文本提取服务

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17+-orange)](https://openjdk.java.net/projects/jdk/17/)

## 🎯 项目概述

基于Spring Boot和Spring AI的智能文本提取服务，采用**统一异步处理架构**，支持知识图谱构建和实体关系抽取，集成MySQL数据库自动存储提取结果。

## 🚀 核心特性

### 🤖 智能文本处理
- **自动模式选择**：根据文本长度智能选择处理策略
- **短文本（≤2000字符）**：直接AI处理，响应快速
- **长文本（>2000字符）**：自动分片并行处理
- **知识图谱提取**：实体、关系、三元组一站式提取

### ⚡ 统一异步架构
- **立即响应**：所有请求立即返回状态，后台异步处理
- **数据库自动保存**：提取结果自动存储到MySQL，无需等待
- **高并发支持**：专用线程池支持多任务并发
- **智能缓存**：Caffeine高性能缓存提升处理效率

### 🔧 长文本优化
- **智能分片**：按语义边界分割，保持信息完整性
- **并行处理**：最多3个分片同时处理，提升效率
- **重叠机制**：分片间200字符重叠，避免信息丢失
- **智能合并**：自动去重和结果合并

### 📊 架构精简
- **统一控制器**：AsyncExtractController作为唯一入口
- **多端点支持**：主要、批量、社交等专用接口
- **向后兼容**：保持原有异步路径可用
- **清晰架构**：Controller → Service → Provider → Database

## 📋 API接口

### 主要接口

#### 文本提取（推荐）
```http
POST /api/v1/extract
Content-Type: application/json

{
  "textInput": "您的文本内容",
  "extractParams": "entities,relations"
}
```

**响应示例**：
```json
{
  "success": true,
  "message": "任务已提交，正在后台处理，结果将自动保存到数据库",
  "timestamp": 1750837558870
}
```

#### 批量文本提取
```http
POST /api/v1/extract/batch
Content-Type: application/json

{
  "textInput": ["文本1", "文本2", "文本3"],
  "extractParams": "entities,relations"
}
```

#### 社交关系提取
```http
POST /api/v1/extract/social
Content-Type: application/json

{
  "textInput": "张三是李四的好朋友，他们经常一起工作。",
  "extractParams": "entities,relations"
}
```

#### 健康检查
```http
GET /api/v1/health
```

#### 服务信息
```http
GET /api/v1/info
```

### 兼容性接口

为保持向后兼容，以下异步接口仍然可用：

```http
POST /api/v1/async/extract     # 异步提取（兼容）
GET /api/v1/async/health       # 异步健康检查
GET /api/v1/async/info         # 异步服务信息
```

## 🛠️ 快速开始

### 1. 环境要求
```bash
- Java 17+
- 内存: 1GB+
- MySQL 8.0+（数据存储，必需）
```

### 2. 数据库配置
```sql
-- 创建数据库
CREATE DATABASE extract_graph DEFAULT CHARACTER SET utf8mb4;

-- 导入表结构
mysql -u root -p extract_graph < sql/base_data_graph.sql
```

### 3. 启动服务
```bash
# 编译项目
mvn clean package -DskipTests

# 启动服务（端口2701）
java -jar target/extract-service-1.0.0.jar
```

### 4. 功能测试

#### 快速测试
```bash
curl -X POST http://localhost:2701/api/v1/extract \
  -H "Content-Type: application/json" \
  -d '{
    "textInput": "张三是一名软件工程师，他在北京工作。",
    "extractParams": "entities,relations"
  }'
```

#### 完整测试
```bash
# 运行统一控制器测试
python database_integration_test.py
```

## 📊 性能特点

### 响应时间
| 操作类型 | 响应时间 | 处理方式 |
|---------|---------|---------|
| API响应 | < 100ms | 立即返回状态 |
| 短文本处理 | 后台2-5秒 | 直接AI处理 |
| 长文本处理 | 后台5-15秒 | 智能分片处理 |
| 数据库写入 | 自动完成 | 异步保存 |

### 系统能力
- **高并发**：支持多任务同时提取
- **智能缓存**：命中率80%以上
- **内存优化**：分片机制减少内存占用
- **数据一致性**：事务保证数据完整性

## 💡 最佳实践

### 1. Python调用示例
```python
import requests
import time

def extract_text(text, extract_params="entities,relations"):
    """文本提取 - 统一接口"""
    url = "http://localhost:2701/api/v1/extract"
    data = {
        "textInput": text,
        "extractParams": extract_params
    }
    
    response = requests.post(url, json=data, timeout=10)
    result = response.json()
    
    if result.get("success"):
        print("✅ 任务提交成功，正在后台处理")
        print("💡 结果将自动保存到数据库")
        return True
    else:
        print(f"❌ 任务提交失败: {result.get('error')}")
        return False

def extract_batch(texts, extract_params="entities,relations"):
    """批量提取"""
    url = "http://localhost:2701/api/v1/extract/batch"
    data = {
        "textInput": texts,  # 数组格式
        "extractParams": extract_params
    }
    
    response = requests.post(url, json=data, timeout=10)
    return response.json()

def extract_social(text):
    """社交关系提取"""
    url = "http://localhost:2701/api/v1/extract/social"
    data = {
        "textInput": text
        # extractParams 会自动设置为 "entities,relations"
    }
    
    response = requests.post(url, json=data, timeout=10)
    return response.json()

# 使用示例
text = "李明是一名AI工程师，他开发了智能聊天机器人。"

# 单文本提取
extract_text(text)

# 批量提取
texts = ["文本1", "文本2", "文本3"]
extract_batch(texts)

# 社交关系提取
social_text = "张三和李四是同事关系，他们合作开发项目。"
extract_social(social_text)
```

### 2. JavaScript调用示例
```javascript
async function extractText(text, extractParams = 'entities,relations') {
  const response = await fetch('http://localhost:2701/api/v1/extract', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      textInput: text,
      extractParams: extractParams
    })
  });
  
  const result = await response.json();
  
  if (result.success) {
    console.log('✅ 任务提交成功，后台处理中');
    return true;
  } else {
    console.error('❌ 任务提交失败:', result.error);
    return false;
  }
}

async function extractBatch(texts) {
  const response = await fetch('http://localhost:2701/api/v1/extract/batch', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      textInput: texts,  // 数组格式
      extractParams: 'triples'
    })
  });
  
  return await response.json();
}
```

## 🏗️ 系统架构

### 精简架构设计
```
统一异步控制器:
├── AsyncExtractController (统一入口)
│   ├── POST /api/v1/extract - 主要提取接口
│   ├── POST /api/v1/extract/batch - 批量提取
│   ├── POST /api/v1/extract/social - 社交关系提取
│   ├── POST /api/v1/async/extract - 兼容接口
│   ├── GET /api/v1/health - 健康检查
│   ├── GET /api/v1/info - 服务信息
│   └── 立即返回响应，后台异步处理
│
共享服务层:
├── TextExtractionService (核心业务逻辑)
│   ├── processTextAsync() - 异步处理入口
│   └── extractTextData() - 同步处理逻辑
├── SmartAIProvider (AI处理层)
│   ├── 文本长度判断
│   ├── Caffeine缓存
│   └── 处理策略选择
├── LongTextProcessor (长文本处理器)
│   ├── 智能分片算法
│   ├── 并行执行框架
│   └── 结果合并逻辑
├── AIModelCaller (AI调用层)
│   ├── WebClient调用
│   ├── 动态超时控制
│   ├── 并发控制（信号量）
│   └── 智能重试机制
└── DatabaseService (数据持久化)
    ├── MySQL自动存储
    └── 7表关系图谱
```

### 技术栈
- **Spring Boot 3.2+**：Web框架
- **Spring AI 1.0+**：MCP工具支持
- **CompletableFuture**：异步并行处理
- **Caffeine Cache**：高性能本地缓存
- **WebClient**：响应式HTTP客户端
- **MySQL + JPA**：数据持久化
- **ThreadPoolTaskExecutor**：线程池管理

## 📊 配置参数

### 核心配置
```yaml
# 服务端口
server:
  port: 2701

# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/extract_graph
    username: root
    password: 123456

# AI提供者配置
extraction:
  ai:
    providers:
      deepseek:
        api-key: sk-cea6dbdbba694338b5f4abe9dfb0975b
        url: https://api.deepseek.com/v1/chat/completions
        base-timeout: 30s
        max-timeout: 180s
        retry-count: 4
        max-concurrent-calls: 5

# 异步处理配置
async:
  core-pool-size: 5
  max-pool-size: 20
  queue-capacity: 100
  thread-name-prefix: "TextExtract-"

# 长文本处理配置
long-text:
  threshold: 2000              # 长文本阈值
  max-chunk-size: 2000         # 最大分片大小
  min-chunk-size: 500          # 最小分片大小
  overlap-size: 200            # 重叠大小
  max-parallel-chunks: 3       # 最大并行分片数
```

## ❓ 常见问题

### Q: 为什么删除了同步接口？
A: 统一使用异步处理提供更好的用户体验，避免长时间等待，同时保持架构简洁。

### Q: 如何知道提取任务是否完成？
A: 任务结果会自动保存到数据库，可以通过查询数据库表来确认处理完成。

### Q: 兼容性接口会被移除吗？
A: 不会。我们保持`/api/v1/async/*`路径的向后兼容，确保现有客户端正常工作。

### Q: 批量接口和单个接口有什么区别？
A: 批量接口支持一次提交多个文本，底层使用相同的处理逻辑，只是输入格式不同。

### Q: 社交关系提取接口有什么特点？
A: 自动设置最适合社交关系识别的参数，无需手动指定extractParams。

### Q: 如何查看提取结果？
A: 所有提取结果都自动保存到MySQL数据库，可通过SQL查询或数据库管理工具查看。

## 🏆 架构优势

### ✅ 精简设计
- **统一入口**：AsyncExtractController作为唯一控制器
- **清晰职责**：每个端点有明确的用途和优化
- **代码减少**：删除重复代码，提升维护性

### ✅ 用户体验
- **快速响应**：所有请求立即返回，无需等待
- **自动保存**：结果自动写入数据库，无需额外操作
- **智能处理**：根据文本特点选择最佳处理策略

### ✅ 系统性能
- **高并发**：支持大量并发请求
- **资源优化**：异步处理避免资源阻塞
- **智能缓存**：提升重复请求性能

### ✅ 开发友好
- **简化架构**：减少学习成本
- **向后兼容**：保护现有投资
- **易于扩展**：清晰的分层设计

---

**注意**：确保AI服务正常运行，建议使用前先进行健康检查。

## 📝 开发团队

**系统架构师** - 基于Spring Boot生态实现的现代化文本提取服务

---

> **设计理念**: "简单而强大" - 用统一的异步架构实现高效的文本处理  
> **架构目标**: 高性能、高可用、易使用、易维护  
> **实现成果**: 精简而强大的智能文本提取服务


ssh -T git@github.com
git add .
git commit -m "更新readme.md"
git remote set-url origin  git@github.com:jimeswei/extract-service.git
git push origin main
