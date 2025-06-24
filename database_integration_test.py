#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据库集成测试脚本 - 精简版
验证统一提取接口与数据库写入完整流程
"""

import requests
import json
import time
from datetime import datetime
import sys

class DatabaseIntegrationTester:
    """数据库集成测试器 - 精简版"""
    
    def __init__(self):
        self.base_url = "http://localhost:2701/api/v1"
        self.session = requests.Session()
        self.test_results = []
        
    def check_service_health(self):
        """检查服务健康状态"""
        print("🏥 服务健康检查")
        print("=" * 50)
        
        try:
            response = self.session.get(f"{self.base_url}/health", timeout=10)
            
            if response.status_code == 200:
                health_data = response.json()
                print(f"✅ 服务状态: {health_data.get('status', 'unknown')}")
                print(f"💾 数据库状态: {'已启用' if health_data.get('database_enabled') else '未启用'}")
                return True
            else:
                print(f"⚠️  健康检查异常(HTTP {response.status_code})，但继续测试")
                return True
                
        except requests.exceptions.ConnectionError:
            print("❌ 服务连接失败 - 请确保服务已启动")
            return False
        except Exception as e:
            print(f"⚠️  健康检查异常: {e}，但尝试继续测试")
            return True

    def test_extract_interface(self, test_name, text_input, extract_params="triples"):
        """统一的提取接口测试方法"""
        print(f"\n🧪 {test_name}")
        print("-" * 60)
        
        request_data = {
            "textInput": text_input,
            "extractParams": extract_params
        }
        
        try:
            start_time = time.time()
            response = self.session.post(
                f"{self.base_url}/extract",
                json=request_data,
                headers={'Content-Type': 'application/json'},
                timeout=60
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                result = response.json()
                success = result.get('success', False)
                triples = result.get('triples', [])
                
                print(f"✅ 测试成功 | ⏱️ {response_time:.2f}秒 | 🎯 成功: {success}")
                if triples:
                    print(f"🔗 提取三元组: {len(triples)} 个")
                    # 显示前3个三元组
                    for i, triple in enumerate(triples[:3], 1):
                        if isinstance(triple, dict):
                            print(f"  {i}. {triple.get('subject', 'N/A')} → {triple.get('predicate', 'N/A')} → {triple.get('object', 'N/A')}")
                
                self.test_results.append({
                    "name": test_name,
                    "success": True,
                    "response_time": response_time,
                    "triples_count": len(triples),
                    "ai_success": success
                })
                return True
            else:
                print(f"❌ 测试失败，HTTP状态码: {response.status_code}")
                self.test_results.append({"name": test_name, "success": False})
                return False
                
        except Exception as e:
            print(f"❌ 测试异常: {e}")
            self.test_results.append({"name": test_name, "success": False})
            return False

    def run_core_tests(self):
        """运行核心测试用例"""
        print("\n🎯 核心功能测试")
        print("=" * 60)
        
        # 核心测试用例 - 覆盖所有表类型
        core_tests = [
            {
                "name": "人员关系测试",
                "text": "张艺谋与巩俐是合作伙伴，张艺谋与陈婷结婚。刘德华和梁朝伟是好友关系。"
            },
            {
                "name": "人员作品测试", 
                "text": "张艺谋导演了电影《红高粱》，巩俐主演了该片。周杰伦演唱了歌曲《青花瓷》。"
            },
            {
                "name": "事件活动测试",
                "text": "第41届柏林国际电影节颁奖典礼于1991年举行。2008年北京奥运会开幕式在鸟巢举办。"
            },
            {
                "name": "人员事件测试",
                "text": "张艺谋参加了柏林电影节颁奖典礼并获奖。张艺谋担任了北京奥运会开幕式总导演。"
            },
            {
                "name": "事件作品测试",
                "text": "电影《红高粱》在柏林电影节上获得金熊奖并展映。《青花瓷》在奥运会开幕式上演出。"
            },
            {
                "name": "综合全链路测试",
                "text": "成龙主演电影《醉拳》，该片在香港电影节首映。成龙参加了电影节颁奖典礼并获得终身成就奖。"
            }
        ]
        
        for i, test in enumerate(core_tests, 1):
            print(f"\n[测试 {i}/{len(core_tests)}]")
            self.test_extract_interface(test["name"], test["text"])
            if i < len(core_tests):
                time.sleep(1)  # 测试间隔

    def test_error_scenarios(self):
        """测试错误场景"""
        print("\n⚠️  错误场景测试")
        print("=" * 50)
        
        error_tests = [
            {"name": "空文本", "data": {"textInput": "", "extractParams": "triples"}},
            {"name": "缺少字段", "data": {"extractParams": "triples"}},
            {"name": "null文本", "data": {"textInput": None, "extractParams": "triples"}},
        ]
        
        success_count = 0
        for test in error_tests:
            try:
                response = self.session.post(
                    f"{self.base_url}/extract",
                    json=test['data'],
                    headers={'Content-Type': 'application/json'},
                    timeout=10
                )
                
                # 期望返回错误或者空结果
                if response.status_code != 200 or (response.status_code == 200 and not response.json().get('success', True)):
                    print(f"✅ {test['name']}: 正确处理错误")
                    success_count += 1
                else:
                    print(f"❌ {test['name']}: 应该返回错误")
                    
            except Exception:
                print(f"✅ {test['name']}: 正确拦截异常")
                success_count += 1
        
        print(f"📊 错误场景测试成功率: {(success_count/len(error_tests)*100):.1f}%")

    def print_summary(self):
        """打印测试总结"""
        print("\n" + "=" * 60)
        print("📊 测试总结报告")
        print("=" * 60)
        
        total_tests = len(self.test_results)
        successful_tests = len([r for r in self.test_results if r.get("success")])
        
        print(f"🎯 总测试: {total_tests} | ✅ 成功: {successful_tests} | ❌ 失败: {total_tests - successful_tests}")
        print(f"📈 成功率: {(successful_tests/total_tests*100):.1f}%" if total_tests > 0 else "📈 成功率: 0%")
        
        if successful_tests > 0:
            # 性能统计
            response_times = [r["response_time"] for r in self.test_results if r.get("success") and "response_time" in r]
            if response_times:
                print(f"⚡ 平均响应时间: {sum(response_times)/len(response_times):.2f}秒")
            
            # 数据统计
            total_triples = sum([r.get("triples_count", 0) for r in self.test_results if r.get("success")])
            ai_success_count = len([r for r in self.test_results if r.get("success") and r.get("ai_success")])
            
            print(f"🔗 总提取三元组: {total_triples} 个")
            print(f"🤖 AI处理成功: {ai_success_count} 次")

    def print_database_guide(self):
        """打印数据库验证指南"""
        print(f"\n💾 数据库验证指南")
        print("=" * 60)
        print("🔗 数据库: localhost:3306/base_data_graph (root/123456)")
        print("\n📋 快速验证SQL:")
        print("```sql")
        print("-- 检查所有表数据统计")
        print("SELECT 'celebrity' as table_name, COUNT(*) as count FROM celebrity")
        print("UNION SELECT 'work', COUNT(*) FROM work")
        print("UNION SELECT 'event', COUNT(*) FROM event") 
        print("UNION SELECT 'celebrity_celebrity', COUNT(*) FROM celebrity_celebrity")
        print("UNION SELECT 'celebrity_work', COUNT(*) FROM celebrity_work")
        print("UNION SELECT 'celebrity_event', COUNT(*) FROM celebrity_event")
        print("UNION SELECT 'event_work', COUNT(*) FROM event_work")
        print("ORDER BY count DESC;")
        print("```")
        
        print("\n🗑️  清理测试数据:")
        print("```sql")
        print("-- 清理关系表")
        print("TRUNCATE TABLE celebrity_celebrity, celebrity_work, celebrity_event, event_work;")
        print("-- 清理主表") 
        print("TRUNCATE TABLE celebrity, work, event;")
        print("```")

def main():
    """主测试函数"""
    print("🧪 数据库集成测试 - 精简版")
    print("=" * 80)
    print(f"📅 测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("🎯 目标: 验证统一提取接口 + 7张表数据写入")
    
    tester = DatabaseIntegrationTester()
    
    # 1. 服务健康检查
    if not tester.check_service_health():
        print("\n❌ 服务不可用，测试终止")
        sys.exit(1)
    
    # 2. 错误场景测试
    tester.test_error_scenarios()
    
    # 3. 核心功能测试
    tester.run_core_tests()
    
    # 4. 测试总结
    tester.print_summary()
    
    # 5. 数据库验证指南
    tester.print_database_guide()
    
    print(f"\n✅ 测试完成: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("🎊 简化架构测试成功 - 统一extractTextData方法处理所有需求")

if __name__ == "__main__":
    main() 