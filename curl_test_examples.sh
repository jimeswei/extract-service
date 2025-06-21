#!/bin/bash

# 智能文本提取服务 - cURL 测试示例
# 使用方法：chmod +x curl_test_examples.sh && ./curl_test_examples.sh

BASE_URL="http://localhost:2701"

echo "🚀 开始测试智能文本提取服务..."
echo "======================================"

# 1. 健康检查
echo "1️⃣ 健康检查测试"
curl -X GET "$BASE_URL/api/v1/health" \
  -H "Content-Type: application/json" \
  -s | jq '.'
echo -e "\n"

# 2. 服务信息
echo "2️⃣ 服务信息测试"
curl -X GET "$BASE_URL/api/v1/info" \
  -H "Content-Type: application/json" \
  -s | jq '.'
echo -e "\n"

# 3. 单文本提取
echo "3️⃣ 单文本三元组提取测试"
curl -X POST "$BASE_URL/api/v1/extract" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "小明是北京大学的学生，他正在学习人工智能课程。",
    "extractType": "triples"
  }' \
  -s | jq '.'
echo -e "\n"

# 4. 复杂文本提取
echo "4️⃣ 复杂文本提取测试"
curl -X POST "$BASE_URL/api/v1/extract" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "苹果公司成立于1976年，由史蒂夫·乔布斯、史蒂夫·沃兹尼亚克和罗纳德·韦恩共同创立。公司总部位于加利福尼亚州库比蒂诺，是全球领先的科技公司之一。",
    "extractType": "triples"
  }' \
  -s | jq '.'
echo -e "\n"

# 5. 批量文本提取
echo "5️⃣ 批量文本提取测试"
curl -X POST "$BASE_URL/api/v1/extract/batch" \
  -H "Content-Type: application/json" \
  -d '{
    "texts": [
      "张三是清华大学的教授。",
      "李四在谷歌公司工作。",
      "王五喜欢打篮球。"
    ],
    "extractType": "triples"
  }' \
  -s | jq '.'
echo -e "\n"

# 6. MCP初始化
echo "6️⃣ MCP初始化测试"
curl -X POST "$BASE_URL/mcp/initialize" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {
        "tools": {}
      },
      "clientInfo": {
        "name": "curl-client",
        "version": "1.0.0"
      }
    }
  }' \
  -s | jq '.'
echo -e "\n"

# 7. MCP工具列表
echo "7️⃣ MCP工具列表测试"
curl -X POST "$BASE_URL/mcp/tools/list" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }' \
  -s | jq '.'
echo -e "\n"

# 8. MCP工具调用 - 提取三元组
echo "8️⃣ MCP工具调用 - 提取三元组测试"
curl -X POST "$BASE_URL/mcp/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
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
  }' \
  -s | jq '.'
echo -e "\n"

# 9. MCP工具调用 - 批量提取
echo "9️⃣ MCP工具调用 - 批量提取测试"
curl -X POST "$BASE_URL/mcp/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "batch_extract",
      "arguments": {
        "texts": "[\"腾讯公司由马化腾创立。\",\"百度是中国的搜索引擎公司。\"]",
        "extractType": "triples",
        "batchSize": "10"
      }
    }
  }' \
  -s | jq '.'
echo -e "\n"

# 10. MCP工具调用 - 健康检查
echo "🔟 MCP工具调用 - 健康检查测试"
curl -X POST "$BASE_URL/mcp/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 5,
    "method": "tools/call",
    "params": {
      "name": "health_check",
      "arguments": {}
    }
  }' \
  -s | jq '.'
echo -e "\n"

# 11. 错误处理测试 - 空文本
echo "1️⃣1️⃣ 错误处理测试 - 空文本"
curl -X POST "$BASE_URL/api/v1/extract" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "",
    "extractType": "triples"
  }' \
  -s | jq '.'
echo -e "\n"

# 12. 错误处理测试 - 无效参数
echo "1️⃣2️⃣ 错误处理测试 - 无效参数"
curl -X POST "$BASE_URL/api/v1/extract" \
  -H "Content-Type: application/json" \
  -d '{
    "invalidParam": "test"
  }' \
  -s | jq '.'
echo -e "\n"

# 13. 性能测试 - 长文本
echo "1️⃣3️⃣ 性能测试 - 长文本提取"
curl -X POST "$BASE_URL/api/v1/extract" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "人工智能（Artificial Intelligence，AI）是计算机科学的一个分支，它企图了解智能的实质，并生产出一种新的能以人类智能相似的方式做出反应的智能机器。该领域的研究包括机器人、语言识别、图像识别、自然语言处理和专家系统等。人工智能从诞生以来，理论和技术日益成熟，应用领域也不断扩大。可以设想，未来人工智能带来的科技产品，将会是人类智慧的\"容器\"。人工智能可以对人的意识、思维的信息过程的模拟。",
    "extractType": "triples"
  }' \
  -w "\n⏱️  响应时间: %{time_total}s\n" \
  -s | jq '.'
echo -e "\n"

echo "✅ 所有测试完成！"
echo "======================================"

# 使用说明
echo "📝 使用说明："
echo "1. 确保服务已启动：mvn spring-boot:run"
echo "2. 安装jq工具：brew install jq (macOS) 或 apt-get install jq (Ubuntu)"
echo "3. 运行脚本：./curl_test_examples.sh"
echo "4. 查看详细日志：tail -f log/application.log" 