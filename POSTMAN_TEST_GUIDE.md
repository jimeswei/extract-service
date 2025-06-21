# 智能文本提取服务 - Postman 测试指南

## 📋 概述

本文档提供了对**智能文本提取服务**进行完整API测试的Postman集合和测试指南。该服务实现了MCP（Model Context Protocol）标准，提供了智能文本提取和知识三元组生成功能。

## 🚀 快速开始

### 1. 导入Postman集合

1. 打开Postman应用
2. 点击 **Import** 按钮
3. 选择 `postman_collection.json` 文件
4. 导入完成后，你将看到 "智能文本提取服务 - Extract Service API" 集合

### 2. 环境配置

集合已预配置了以下变量：
- `base_url`: `http://localhost:2701` (默认本地服务地址)

如需修改服务地址，请更新集合变量中的 `base_url` 值。

### 3. 启动服务

确保服务已启动并运行在配置的端口上：
```bash
# 在项目根目录执行
mvn spring-boot:run
```

## 📁 测试集合结构

### 1. 基础API测试
- **健康检查** - 检查服务运行状态
- **服务信息** - 获取服务基本信息

### 2. 文本提取API
- **单文本三元组提取** - 提取单个文本的知识三元组
- **复杂文本提取** - 处理复杂长文本
- **批量文本提取** - 批量处理多个文本

### 3. MCP工具接口测试
- **MCP初始化** - 初始化MCP连接
- **MCP工具列表** - 获取可用工具列表
- **MCP工具调用** - 调用具体的MCP工具

### 4. 错误处理测试
- **空文本测试** - 测试空输入处理
- **无效参数测试** - 测试参数验证

### 5. 性能测试
- **长文本提取** - 测试大文本处理能力
- **大批量提取** - 测试批量处理性能

## 🔧 详细测试说明

### 基础API测试

#### 健康检查
```http
GET /api/v1/health
```
**预期响应**：
```json
{
  "service": "extract-service",
  "status": "healthy",
  "memory_usage": "45.67%",
  "timestamp": 1703123456789
}
```

#### 服务信息
```http
GET /api/v1/info
```
**预期响应**：
```json
{
  "service": "intelligent-extraction-service",
  "version": "1.0.0",
  "description": "按照系统架构设计文档实现的智能文本提取服务",
  "features": ["知识三元组提取", "智能缓存", "容错降级", "批量处理"]
}
```

### 文本提取API测试

#### 单文本提取
```http
POST /api/v1/extract
Content-Type: application/json

{
  "text": "小明是北京大学的学生，他正在学习人工智能课程。",
  "extractType": "triples"
}
```

**预期响应**：
```json
{
  "triples": [
    ["小明", "是", "学生"],
    ["小明", "就读于", "北京大学"],
    ["小明", "学习", "人工智能课程"]
  ],
  "success": true,
  "timestamp": 1703123456789
}
```

#### 批量文本提取
```http
POST /api/v1/extract/batch
Content-Type: application/json

{
  "texts": [
    "张三是清华大学的教授。",
    "李四在谷歌公司工作。",
    "王五喜欢打篮球。"
  ],
  "extractType": "triples"
}
```

### MCP工具接口测试

#### MCP初始化
```http
POST /mcp/initialize
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {}
    },
    "clientInfo": {
      "name": "postman-client",
      "version": "1.0.0"
    }
  }
}
```

#### MCP工具列表
```http
POST /mcp/tools/list
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

**预期响应**：
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "extract_triples",
        "description": "从文本中提取知识三元组，自动识别实体和关系"
      },
      {
        "name": "batch_extract",
        "description": "批量提取多个文本的知识三元组，自动并行处理提升效率"
      },
      {
        "name": "health_check",
        "description": "检查服务健康状态，包括AI API可用性和系统资源状态"
      }
    ]
  }
}
```

#### MCP工具调用
```http
POST /mcp/tools/call
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "extract_triples",
    "arguments": {
      "text": "马云是阿里巴巴集团的创始人，公司成立于1999年。",
      "extractType": "triples",
      "options": "{}"
    }
  }
}
```

## 🧪 测试场景说明

### 1. 功能验证测试
- ✅ 基础API可用性
- ✅ 文本提取准确性
- ✅ MCP协议兼容性
- ✅ 批量处理能力

### 2. 边界条件测试
- ✅ 空文本处理
- ✅ 超长文本处理
- ✅ 特殊字符处理
- ✅ 无效参数处理

### 3. 性能压力测试
- ✅ 响应时间验证（< 30秒）
- ✅ 并发处理能力
- ✅ 内存使用监控
- ✅ 缓存效果验证

### 4. 错误处理测试
- ✅ 网络异常处理
- ✅ AI API失败降级
- ✅ 参数验证错误
- ✅ 系统异常恢复

## 📊 测试自动化

### 集合级测试脚本

每个请求都包含以下自动化测试：

```javascript
// 响应状态码检查
pm.test('响应状态码检查', function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201, 202]);
});

// 响应时间检查
pm.test('响应时间检查', function () {
    pm.expect(pm.response.responseTime).to.be.below(30000);
});

// 响应格式检查
pm.test('响应格式检查', function () {
    pm.expect(pm.response.headers.get('Content-Type')).to.include('application/json');
});
```

### 运行批量测试

1. 选择整个集合
2. 点击 **Run** 按钮
3. 配置测试参数：
   - Iterations: 1
   - Delay: 1000ms
   - Data: 无需数据文件
4. 点击 **Start Test** 开始测试

## 🔍 测试结果分析

### 成功指标
- ✅ 所有测试用例通过
- ✅ 响应时间 < 30秒
- ✅ 内存使用率 < 90%
- ✅ 缓存命中率 > 50%

### 失败排查
1. **连接失败**：检查服务是否启动，端口是否正确
2. **超时错误**：检查AI API配置，网络连接状态
3. **参数错误**：检查请求体格式，参数完整性
4. **内存不足**：检查系统资源，重启服务

## 📈 性能基准

### 响应时间基准
- 健康检查：< 100ms
- 单文本提取：< 5s
- 批量提取（10条）：< 15s
- MCP工具调用：< 8s

### 吞吐量基准
- 单文本处理：~12 req/min
- 批量处理：~5 batch/min
- 并发用户：支持10个并发

## 🛠️ 故障排除

### 常见问题

1. **服务启动失败**
   ```bash
   # 检查端口占用
   lsof -i :2701
   
   # 检查Java进程
   ps aux | grep java
   ```

2. **AI API调用失败**
   - 检查API密钥配置
   - 验证网络连接
   - 查看日志文件 `log/application.log`

3. **内存不足**
   ```bash
   # 调整JVM参数
   export JAVA_OPTS="-Xmx2g -Xms1g"
   mvn spring-boot:run
   ```

### 日志分析
```bash
# 查看实时日志
tail -f log/application.log

# 搜索错误信息
grep "ERROR" log/application.log
```

## 📝 测试报告模板

### 测试执行报告
```
测试日期：2024-01-01
测试环境：本地开发环境
测试用例总数：15
通过用例：14
失败用例：1
通过率：93.3%

详细结果：
✅ 基础API测试：2/2 通过
✅ 文本提取API：3/3 通过
✅ MCP工具接口：5/5 通过
✅ 错误处理测试：2/2 通过
❌ 性能测试：2/3 通过（长文本超时）

建议：
1. 优化长文本处理算法
2. 增加超时配置选项
3. 添加更多边界测试用例
```

## 🔗 相关资源

- [MCP协议规范](https://spec.modelcontextprotocol.io/)
- [Spring AI文档](https://docs.spring.io/spring-ai/reference/)
- [Postman测试指南](https://learning.postman.com/docs/writing-scripts/test-scripts/)

---

**注意**：在生产环境测试前，请确保已正确配置所有环境变量和API密钥。 