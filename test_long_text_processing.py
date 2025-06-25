#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
长文本分批次处理功能测试脚本
测试知识图谱提取服务对超长文本的处理能力
"""

import requests
import json
import time
from typing import Dict, Any
from datetime import datetime

# 服务配置
BASE_URL = "http://localhost:2701"
ASYNC_EXTRACT_URL = f"{BASE_URL}/api/v1/async/extract"
HEALTH_URL = f"{BASE_URL}/api/v1/async/health"
INFO_URL = f"{BASE_URL}/api/v1/async/info"

def print_test_header(title):
    """打印测试标题"""
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")

def print_response(response_data, title="响应"):
    """格式化打印响应数据"""
    print(f"\n{title}:")
    if isinstance(response_data, dict):
        print(json.dumps(response_data, ensure_ascii=False, indent=2))
    else:
        print(response_data)

def test_health_check():
    """测试健康检查"""
    print_test_header("健康检查测试")
    
    try:
        response = requests.get(HEALTH_URL, timeout=10)
        response.raise_for_status()
        
        data = response.json()
        print_response(data, "健康检查响应")
        
        if data.get("status") == "healthy":
            print("✅ 服务状态正常")
            return True
        else:
            print("❌ 服务状态异常")
            return False
            
    except Exception as e:
        print(f"❌ 健康检查失败: {e}")
        return False

def test_service_info():
    """测试服务信息"""
    print_test_header("服务信息测试")
    
    try:
        response = requests.get(INFO_URL, timeout=10)
        response.raise_for_status()
        
        data = response.json()
        print_response(data, "服务信息")
        print("✅ 服务信息获取成功")
        return True
        
    except Exception as e:
        print(f"❌ 服务信息获取失败: {e}")
        return False

def test_short_text():
    """测试短文本处理（直接AI模式）"""
    print_test_header("短文本处理测试")
    
    short_text = """
    张三是一名软件工程师，他在北京的一家科技公司工作。
    他负责开发人工智能系统，经常与李四和王五合作。
    他们的团队正在开发一个智能对话系统。
    """
    
    print(f"测试文本长度: {len(short_text.strip())} 字符")
    print(f"预期处理模式: 直接AI处理（小于2000字符阈值）")
    
    payload = {
        "textInput": short_text.strip(),
        "extractParams": "entities,relations"
    }
    
    try:
        start_time = time.time()
        response = requests.post(ASYNC_EXTRACT_URL, json=payload, timeout=30)
        end_time = time.time()
        
        response.raise_for_status()
        data = response.json()
        
        print_response(data, "短文本处理响应")
        print(f"⏱️  响应时间: {end_time - start_time:.2f} 秒")
        
        if data.get("success"):
            print("✅ 短文本处理成功")
            return True
        else:
            print("❌ 短文本处理失败")
            return False
            
    except Exception as e:
        print(f"❌ 短文本处理异常: {e}")
        return False

def test_long_text():
    """测试长文本处理（分批处理模式）"""
    print_test_header("长文本处理测试")
    
    # 构造超过2000字符的长文本
    long_text = """
    在人工智能快速发展的今天，自然语言处理技术已经成为了计算机科学领域的重要分支。张伟博士作为清华大学计算机科学系的教授，一直专注于机器学习和深度学习的研究。他的团队开发了多个重要的算法，包括基于Transformer架构的预训练语言模型。

    李明是张伟教授团队中的博士研究生，他专门研究知识图谱的构建和推理技术。在过去的两年中，李明发表了十多篇高质量的学术论文，其中包括在AAAI和IJCAI等顶级会议上的论文。他的研究工作主要集中在实体识别、关系抽取和知识融合等方面。

    王小华是团队中的另一位重要成员，她负责数据挖掘和文本分析的相关工作。王小华毕业于北京大学数学系，后来转入计算机领域。她开发的文本处理算法能够有效地从大规模文档中提取结构化信息。

    在最近的一个项目中，团队与百度公司、阿里巴巴集团和腾讯科技进行了深度合作。这个项目的目标是构建一个大规模的中文知识图谱，包含数百万个实体和数千万个关系三元组。项目使用了最新的BERT模型和GPT架构，结合了传统的规则学习方法。

    赵教授作为项目的总负责人，协调各方面的工作。他曾经在斯坦福大学和MIT进行过访问学者研究，具有丰富的国际合作经验。在他的领导下，团队成功地将理论研究与产业应用相结合。

    项目的第一阶段已经完成，团队成功地从维基百科、百度百科和其他在线资源中抽取了大量的结构化知识。第二阶段将重点关注知识的质量评估和错误纠正。第三阶段计划将知识图谱应用到智能问答系统、推荐系统和搜索引擎中。

    在技术实现方面，团队使用了多种先进的机器学习框架，包括PyTorch、TensorFlow和Paddle。他们还开发了自己的分布式训练系统，能够在数百个GPU上并行训练大型模型。

    除了技术研究，团队还非常重视产业化应用。他们与多家科技公司签署了技术转让协议，将研究成果转化为实际的产品和服务。这些合作不仅推动了学术研究的发展，也为社会创造了实际价值。

    未来，团队计划继续深入研究多模态知识图谱，结合文本、图像和视频等多种数据源。他们还将探索知识图谱在医疗、金融和教育等垂直领域的应用潜力。

    总的来说，这个团队在人工智能和知识图谱领域取得了显著的成就，为相关领域的发展做出了重要贡献。
    """ * 2  # 复制一遍确保超过2000字符
    
    print(f"测试文本长度: {len(long_text.strip())} 字符")
    print(f"预期处理模式: 分批处理（超过2000字符阈值）")
    
    payload = {
        "textInput": long_text.strip(),
        "extractParams": "entities,relations"
    }
    
    try:
        start_time = time.time()
        response = requests.post(ASYNC_EXTRACT_URL, json=payload, timeout=30)
        end_time = time.time()
        
        response.raise_for_status()
        data = response.json()
        
        print_response(data, "长文本处理响应")
        print(f"⏱️  响应时间: {end_time - start_time:.2f} 秒")
        
        if data.get("success"):
            print("✅ 长文本处理成功")
            return True
        else:
            print("❌ 长文本处理失败")
            return False
            
    except Exception as e:
        print(f"❌ 长文本处理异常: {e}")
        return False

def test_medium_text():
    """测试中等长度文本处理"""
    print_test_header("中等文本处理测试")
    
    medium_text = """
    在现代软件开发中，微服务架构已经成为了主流的系统设计模式。张工程师作为架构师，负责设计和实现公司的核心业务系统。他的团队包括李开发者、王测试工程师和赵运维专家。

    他们正在开发一个电商平台，系统包含用户服务、商品服务、订单服务和支付服务等多个微服务。每个服务都有独立的数据库和部署单元。系统使用了Spring Boot框架、Redis缓存、MySQL数据库和Kafka消息队列。

    在项目实施过程中，团队采用了敏捷开发方法，每两周进行一次迭代。他们使用Jenkins进行持续集成，Docker进行容器化部署，Kubernetes进行服务编排。整个系统部署在阿里云上，使用了负载均衡、自动扩缩容等云原生特性。

    项目的挑战主要来自于服务间的协调和数据一致性问题。团队通过实施分布式事务、事件驱动架构和最终一致性原理来解决这些问题。他们还建立了完善的监控体系，使用Prometheus和Grafana进行系统监控。
    """
    
    print(f"测试文本长度: {len(medium_text.strip())} 字符")
    print(f"预期处理模式: 直接AI处理（接近但未超过2000字符阈值）")
    
    payload = {
        "textInput": medium_text.strip(),
        "extractParams": "entities,relations"
    }
    
    try:
        start_time = time.time()
        response = requests.post(ASYNC_EXTRACT_URL, json=payload, timeout=30)
        end_time = time.time()
        
        response.raise_for_status()
        data = response.json()
        
        print_response(data, "中等文本处理响应")
        print(f"⏱️  响应时间: {end_time - start_time:.2f} 秒")
        
        if data.get("success"):
            print("✅ 中等文本处理成功")
            return True
        else:
            print("❌ 中等文本处理失败")
            return False
            
    except Exception as e:
        print(f"❌ 中等文本处理异常: {e}")
        return False

def main():
    """主测试流程"""
    print("🚀 开始长文本分批处理功能测试")
    print(f"测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"目标服务: {BASE_URL}")
    
    results = {}
    
    # 1. 健康检查
    results['health'] = test_health_check()
    if not results['health']:
        print("\n❌ 服务不可用，终止测试")
        return
    
    # 2. 服务信息
    results['info'] = test_service_info()
    
    # 3. 短文本测试
    results['short_text'] = test_short_text()
    time.sleep(2)  # 等待异步处理完成
    
    # 4. 中等文本测试
    results['medium_text'] = test_medium_text()
    time.sleep(2)
    
    # 5. 长文本测试
    results['long_text'] = test_long_text()
    time.sleep(3)  # 长文本需要更多时间
    
    # 测试结果汇总
    print_test_header("测试结果汇总")
    total_tests = len(results)
    passed_tests = sum(1 for result in results.values() if result)
    
    print(f"总测试项: {total_tests}")
    print(f"通过测试: {passed_tests}")
    print(f"失败测试: {total_tests - passed_tests}")
    print(f"成功率: {passed_tests/total_tests*100:.1f}%")
    
    for test_name, result in results.items():
        status = "✅ 通过" if result else "❌ 失败"
        print(f"  {test_name}: {status}")
    
    if passed_tests == total_tests:
        print("\n🎉 所有测试通过！长文本分批处理功能运行正常。")
    else:
        print(f"\n⚠️  有 {total_tests - passed_tests} 项测试失败，请检查日志。")

if __name__ == "__main__":
    main() 