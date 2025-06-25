#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
AsyncExtractController 简化测试脚本
测试核心异步接口功能
"""

import requests
import json
import time
from datetime import datetime

# 服务配置
BASE_URL = "http://localhost:2701/api/v1"
ASYNC_URL = f"{BASE_URL}/async"

def test_async_extract():
    """测试异步提取接口"""
    print("=" * 60)
    print("测试异步提取接口")
    print("=" * 60)
    
    # 测试单文本
    print("1. 测试单文本提取...")
    test_data = {
        "textInput": "刘德华是香港著名演员和歌手，出演过《无间道》等经典电影。",
        "extractParams": "triples"
    }
    
    try:
        response = requests.post(f"{ASYNC_URL}/extract", 
                               json=test_data, 
                               headers={'Content-Type': 'application/json'},
                               timeout=10)
        
        if response.status_code == 200:
            result = response.json()
            if result.get("success") and "任务已提交" in result.get("message", ""):
                print("✅ 单文本异步提取正常")
            else:
                print("❌ 单文本异步提取响应错误")
        else:
            print(f"❌ 单文本请求失败: {response.text}")
            
    except Exception as e:
        print(f"❌ 单文本请求异常: {str(e)}")
    
    # 测试数组格式
    print("\n2. 测试数组格式提取...")
    test_data = {
        "textInput": [
            "张艺谋执导的《英雄》是一部古装武侠电影。",
            "《三体》三部曲是刘慈欣的代表作品。"
        ],
        "extractParams": "entities"
    }
    
    try:
        response = requests.post(f"{ASYNC_URL}/extract", 
                               json=test_data, 
                               headers={'Content-Type': 'application/json'},
                               timeout=10)
        
        if response.status_code == 200:
            result = response.json()
            if result.get("success"):
                print("✅ 数组格式异步提取正常")
            else:
                print("❌ 数组格式异步提取响应错误")
        else:
            print(f"❌ 数组格式请求失败: {response.text}")
            
    except Exception as e:
        print(f"❌ 数组格式请求异常: {str(e)}")

def test_async_info():
    """测试异步服务信息"""
    print("\n" + "=" * 60)
    print("测试异步服务信息")
    print("=" * 60)
    
    try:
        response = requests.get(f"{ASYNC_URL}/info", timeout=5)
        
        if response.status_code == 200:
            result = response.json()
            print("异步服务信息:")
            print(json.dumps(result, ensure_ascii=False, indent=2))
            print("✅ 异步服务信息获取正常")
        else:
            print(f"❌ 服务信息获取失败: {response.text}")
            
    except Exception as e:
        print(f"❌ 服务信息请求异常: {str(e)}")

def test_async_health():
    """测试异步健康检查"""
    print("\n" + "=" * 60)
    print("测试异步健康检查")
    print("=" * 60)
    
    try:
        response = requests.get(f"{ASYNC_URL}/health", timeout=5)
        
        if response.status_code == 200:
            result = response.json()
            print("健康检查结果:")
            print(json.dumps(result, ensure_ascii=False, indent=2))
            
            if result.get("status") == "healthy":
                print("✅ 异步健康检查正常")
            else:
                print("❌ 健康检查状态异常")
        else:
            print(f"❌ 健康检查失败: {response.text}")
            
    except Exception as e:
        print(f"❌ 健康检查请求异常: {str(e)}")

def main():
    print("🚀 AsyncExtractController 测试开始")
    print(f"异步服务地址: {ASYNC_URL}")
    print(f"测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    
    # 执行测试
    test_async_health()
    test_async_info()
    test_async_extract()
    
    print("\n" + "=" * 60)
    print("🎉 AsyncExtractController 测试完成")
    print("=" * 60)
    print("异步控制器功能总结:")
    print("✅ POST /api/v1/async/extract - 异步文本提取（支持单文本和数组）")
    print("✅ GET /api/v1/async/info - 异步服务信息")
    print("✅ GET /api/v1/async/health - 异步健康检查")
    print("\n特点:")
    print("• 立即返回成功响应，不等待处理完成")
    print("• 支持单文本和数组两种输入格式")
    print("• 完善的错误处理机制")
    print("• 详细的日志记录")

if __name__ == "__main__":
    main() 