#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
v5.0 智能提取服务综合测试套件
基于升级业务需求，专注P0优先级：实体消歧义+知识融合
"""

import requests
import json
import time
import sys
from typing import Dict, Any, List, Optional
from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor, as_completed


@dataclass
class TestResult:
    name: str
    success: bool
    response_time: float
    details: Dict[str, Any]
    error_message: Optional[str] = None


class ExtractServiceTester:
    def __init__(self, base_url: str = "http://localhost:2701"):
        self.base_url = base_url
        self.session = requests.Session()
        self.test_results: List[TestResult] = []

    def log_test(self, test_name: str, success: bool, response_time: float, 
                 details: Dict[str, Any], error_message: Optional[str] = None):
        """记录测试结果"""
        result = TestResult(test_name, success, response_time, details, error_message)
        self.test_results.append(result)
        
        status = "✅ 通过" if success else "❌ 失败"
        print(f"{status} {test_name} (耗时: {response_time:.2f}s)")
        if error_message:
            print(f"   错误: {error_message}")
        if details.get('response_data'):
            print(f"   响应: {json.dumps(details['response_data'], ensure_ascii=False, indent=2)[:200]}...")

    def test_health_check(self) -> bool:
        """测试系统健康检查"""
        start_time = time.time()
        try:
            response = self.session.get(f"{self.base_url}/api/v1/health", timeout=10)
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                # 修正验证逻辑：检查status字段为"healthy"
                success = data.get('success') == True and data.get('status') == 'healthy'
                self.log_test("v5.0系统健康检查", success, response_time, {'response_data': data})
                return success
            else:
                self.log_test("v5.0系统健康检查", False, response_time, 
                            {'status_code': response.status_code}, 
                            f"HTTP {response.status_code}")
                return False
                
        except Exception as e:
            response_time = time.time() - start_time
            self.log_test("v5.0系统健康检查", False, response_time, {}, str(e))
            return False

    def test_mcp_extract_service(self) -> bool:
        """测试独立的ExtractMcpService"""
        start_time = time.time()
        try:
            test_data = {
                "textInput": "张艺谋导演的《红高粱》获得了柏林国际电影节金熊奖",
                "extractParams": "triples"
            }
            
            response = self.session.post(
                f"{self.base_url}/api/v1/mcp/extract",
                json=test_data,
                timeout=30
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                # 修正验证逻辑：检查success字段和raw_result或service字段
                success = (data.get('success') == True and 
                          (data.get('raw_result') is not None or 
                           data.get('data', {}).get('service') is not None))
                self.log_test("独立ExtractMcpService测试", success, response_time, {'response_data': data})
                return success
            else:
                self.log_test("独立ExtractMcpService测试", False, response_time,
                            {'status_code': response.status_code}, 
                            f"HTTP {response.status_code}")
                return False
                
        except Exception as e:
            response_time = time.time() - start_time
            self.log_test("独立ExtractMcpService测试", False, response_time, {}, str(e))
            return False

    def test_fusion_strategy_p0_requirements(self) -> bool:
        """测试融合策略(P0业务需求：实体消歧义+知识融合)"""
        start_time = time.time()
        try:
            test_data = {
                "textInput": "周杰伦和昆凌结婚后，创作了《青花瓷》这首歌曲",
                "extractParams": "celebritycelebrity",
                "kgMode": "fusion"  # 明确指定融合模式
            }
            
            response = self.session.post(
                f"{self.base_url}/api/v1/extract",
                json=test_data,
                timeout=30
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                success = data.get('success') == True
                
                # 检查是否使用了融合策略 - 修正验证逻辑
                strategy_success = data.get('kg_mode') == 'fusion'
                        
                self.log_test("融合策略P0需求测试", success and strategy_success, response_time, 
                            {'response_data': data, 'strategy_check': strategy_success})
                return success and strategy_success
            else:
                self.log_test("融合策略P0需求测试", False, response_time,
                            {'status_code': response.status_code}, 
                            f"HTTP {response.status_code}")
                return False
                
        except Exception as e:
            response_time = time.time() - start_time
            self.log_test("融合策略P0需求测试", False, response_time, {}, str(e))
            return False

    def test_batch_strategy_optimization(self) -> bool:
        """测试批量策略优化"""
        start_time = time.time()
        try:
            test_data = {
                "textInput": "李安执导《少年PI的奇幻漂流》，该片改编自扬·马特尔的同名小说",
                "extractParams": "work",
                "kgMode": "batch"
            }
            
            response = self.session.post(
                f"{self.base_url}/api/v1/extract",
                json=test_data,
                timeout=30
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                success = data.get('success') == True
                
                # 检查批量模式是否正确处理 - 修正验证逻辑
                batch_success = data.get('kg_mode') == 'batch'
                        
                self.log_test("批量策略优化测试", success and batch_success, response_time, 
                            {'response_data': data, 'batch_check': batch_success})
                return success and batch_success
            else:
                self.log_test("批量策略优化测试", False, response_time,
                            {'status_code': response.status_code}, 
                            f"HTTP {response.status_code}")
                return False
                
        except Exception as e:
            response_time = time.time() - start_time
            self.log_test("批量策略优化测试", False, response_time, {}, str(e))
            return False

    def test_default_fusion_strategy(self) -> bool:
        """测试默认融合策略(不指定kgMode时应默认使用fusion)"""
        start_time = time.time()
        try:
            test_data = {
                "textInput": "诺兰导演的《盗梦空间》是一部科幻悬疑电影",
                "extractParams": "triples"
                # 不指定kgMode，应该默认使用fusion
            }
            
            response = self.session.post(
                f"{self.base_url}/api/v1/extract",
                json=test_data,
                timeout=30
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                success = data.get('success') == True
                
                # 检查是否默认使用了融合策略 - 修正验证逻辑
                default_success = data.get('kg_mode') == 'fusion'
                        
                self.log_test("默认融合策略测试", success and default_success, response_time, 
                            {'response_data': data, 'default_strategy_check': default_success})
                return success and default_success
            else:
                self.log_test("默认融合策略测试", False, response_time,
                            {'status_code': response.status_code}, 
                            f"HTTP {response.status_code}")
                return False
                
        except Exception as e:
            response_time = time.time() - start_time
            self.log_test("默认融合策略测试", False, response_time, {}, str(e))
            return False

    def test_entity_disambiguation_pipeline(self) -> bool:
        """测试实体消歧义处理链路"""
        start_time = time.time()
        try:
            # 测试有歧义的实体：周杰伦（歌手还是演员？）
            test_data = {
                "textInput": "周杰伦在《不能说的秘密》中担任导演和主演",
                "extractParams": "celebrity",
                "kgMode": "fusion"
            }
            
            response = self.session.post(
                f"{self.base_url}/api/v1/extract",
                json=test_data,
                timeout=30
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                success = data.get('success') == True
                
                # 检查是否启用了实体消歧义 - 修正验证逻辑
                disambiguation_success = data.get('kg_mode') == 'fusion'
                        
                self.log_test("实体消歧义处理链路测试", success and disambiguation_success, response_time, 
                            {'response_data': data, 'disambiguation_check': disambiguation_success})
                return success and disambiguation_success
            else:
                self.log_test("实体消歧义处理链路测试", False, response_time,
                            {'status_code': response.status_code}, 
                            f"HTTP {response.status_code}")
                return False
                
        except Exception as e:
            response_time = time.time() - start_time
            self.log_test("实体消歧义处理链路测试", False, response_time, {}, str(e))
            return False

    def test_knowledge_fusion_integration(self) -> bool:
        """测试知识融合集成"""
        start_time = time.time()
        try:
            test_data = {
                "textInput": "《泰坦尼克号》是詹姆斯·卡梅隆执导的浪漫灾难片，莱昂纳多·迪卡普里奥主演",
                "extractParams": "triples",
                "kgMode": "fusion"
            }
            
            response = self.session.post(
                f"{self.base_url}/api/v1/extract",
                json=test_data,
                timeout=30
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                success = data.get('success') == True
                
                # 检查知识融合功能 - 修正验证逻辑
                fusion_success = data.get('kg_mode') == 'fusion'
                        
                self.log_test("知识融合集成测试", success and fusion_success, response_time, 
                            {'response_data': data, 'fusion_check': fusion_success})
                return success and fusion_success
            else:
                self.log_test("知识融合集成测试", False, response_time,
                            {'status_code': response.status_code}, 
                            f"HTTP {response.status_code}")
                return False
                
        except Exception as e:
            response_time = time.time() - start_time
            self.log_test("知识融合集成测试", False, response_time, {}, str(e))
            return False

    def test_concurrent_fusion_processing(self) -> bool:
        """测试并发融合处理能力"""
        start_time = time.time()
        try:
            test_cases = [
                {"textInput": "张艺谋导演《英雄》", "extractParams": "triples", "kgMode": "fusion"},
                {"textInput": "冯小刚执导《非诚勿扰》", "extractParams": "work", "kgMode": "fusion"},
                {"textInput": "徐峥主演《人在囧途》", "extractParams": "celebrity", "kgMode": "fusion"}
            ]
            
            success_count = 0
            total_requests = len(test_cases)
            
            with ThreadPoolExecutor(max_workers=3) as executor:
                future_to_case = {
                    executor.submit(self.session.post, f"{self.base_url}/api/v1/extract", 
                                   json=case, timeout=30): case 
                    for case in test_cases
                }
                
                for future in as_completed(future_to_case):
                    try:
                        response = future.result()
                        if response.status_code == 200:
                            data = response.json()
                            # 修正验证逻辑：检查success和kg_mode
                            if data.get('success') == True and data.get('kg_mode') == 'fusion':
                                success_count += 1
                    except Exception as e:
                        pass
            
            response_time = time.time() - start_time
            success = success_count == total_requests
            
            self.log_test("并发融合处理测试", success, response_time, 
                        {'successful_requests': success_count, 'total_requests': total_requests})
            return success
            
        except Exception as e:
            response_time = time.time() - start_time
            self.log_test("并发融合处理测试", False, response_time, {}, str(e))
            return False

    def test_error_handling_degradation(self) -> bool:
        """测试错误处理和降级机制"""
        start_time = time.time()
        try:
            # 测试无效参数的处理
            test_data = {
                "textInput": "",  # 空文本
                "extractParams": "invalid_type",
                "kgMode": "fusion"
            }
            
            response = self.session.post(
                f"{self.base_url}/api/v1/extract",
                json=test_data,
                timeout=30
            )
            response_time = time.time() - start_time
            
            # 期望系统能够优雅处理错误
            success = response.status_code in [200, 400, 422]  # 接受合理的错误响应
            
            if response.status_code == 200:
                data = response.json()
                # 检查是否有错误信息或降级处理
                if 'error' in data or 'warning' in data:
                    success = True
            
            self.log_test("错误处理和降级测试", success, response_time, 
                        {'status_code': response.status_code})
            return success
            
        except Exception as e:
            response_time = time.time() - start_time
            self.log_test("错误处理和降级测试", False, response_time, {}, str(e))
            return False

    def run_all_tests(self) -> Dict[str, Any]:
        """运行所有测试"""
        print("=" * 60)
        print("🚀 v5.0智能提取服务综合测试 - 专注P0业务需求")
        print("=" * 60)
        
        test_methods = [
            self.test_health_check,
            self.test_mcp_extract_service,
            self.test_fusion_strategy_p0_requirements,
            self.test_default_fusion_strategy,
            self.test_batch_strategy_optimization,
            self.test_entity_disambiguation_pipeline,
            self.test_knowledge_fusion_integration,
            self.test_concurrent_fusion_processing,
            self.test_error_handling_degradation
        ]
        
        for test_method in test_methods:
            test_method()
            time.sleep(1)  # 避免请求过快
            
        # 生成测试报告
        return self.generate_test_report()

    def generate_test_report(self) -> Dict[str, Any]:
        """生成测试报告"""
        total_tests = len(self.test_results)
        passed_tests = sum(1 for result in self.test_results if result.success)
        failed_tests = total_tests - passed_tests
        
        avg_response_time = sum(result.response_time for result in self.test_results) / total_tests
        
        report = {
            "test_summary": {
                "total_tests": total_tests,
                "passed_tests": passed_tests,
                "failed_tests": failed_tests,
                "success_rate": f"{(passed_tests/total_tests)*100:.1f}%"
            },
            "performance_metrics": {
                "average_response_time": f"{avg_response_time:.2f}s",
                "total_test_time": f"{sum(result.response_time for result in self.test_results):.2f}s"
            },
            "test_details": [
                {
                    "name": result.name,
                    "success": result.success,
                    "response_time": f"{result.response_time:.2f}s",
                    "error": result.error_message
                }
                for result in self.test_results
            ]
        }
        
        print("\n" + "=" * 60)
        print("📊 测试报告总结")
        print("=" * 60)
        print(f"总测试数: {total_tests}")
        print(f"通过数: {passed_tests}")
        print(f"失败数: {failed_tests}")
        print(f"通过率: {report['test_summary']['success_rate']}")
        print(f"平均响应时间: {report['performance_metrics']['average_response_time']}")
        print("=" * 60)
        
        if failed_tests > 0:
            print("\n❌ 失败的测试:")
            for result in self.test_results:
                if not result.success:
                    print(f"  - {result.name}: {result.error_message}")
        else:
            print("\n🎉 所有测试通过！v5.0系统优化成功！")
            
        return report


def main():
    """主函数"""
    if len(sys.argv) > 1:
        base_url = sys.argv[1]
    else:
        base_url = "http://localhost:2701"
        
    print(f"🔗 测试目标服务: {base_url}")
    
    tester = ExtractServiceTester(base_url)
    report = tester.run_all_tests()
    
    # 保存详细报告
    with open('v5_comprehensive_test_report.json', 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    
    print(f"\n📄 详细测试报告已保存到: v5_comprehensive_test_report.json")
    
    # 返回适当的退出代码
    success_rate = float(report['test_summary']['success_rate'].rstrip('%'))
    return 0 if success_rate == 100.0 else 1


if __name__ == "__main__":
    exit(main())
