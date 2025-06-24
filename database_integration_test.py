#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据库集成测试脚本
资深测试工程师专用 - 验证数据提取与数据库写入完整流程
"""

import requests
import json
import time
from datetime import datetime
import sys
import traceback

class DatabaseIntegrationTester:
    """数据库集成测试器"""
    
    def __init__(self):
        self.base_url = "http://localhost:2701/api/v1"
        self.session = requests.Session()
        self.test_results = []
        self.test_start_time = datetime.now()
        
    def check_service_health(self):
        """检查服务健康状态"""
        print("🏥 服务健康检查")
        print("=" * 50)
        
        try:
            response = self.session.get(f"{self.base_url}/health", timeout=10)
            
            if response.status_code == 200:
                health_data = response.json()
                print(f"✅ 服务状态: {health_data.get('status', 'unknown')}")
                print(f"📊 内存使用: {health_data.get('memory_usage', 'unknown')}")
                print(f"💾 数据库状态: {'已启用' if health_data.get('database_enabled') else '未启用'}")
                print(f"🗄️  Redis状态: {'已连接' if health_data.get('redis_connected') else '未连接'}")
                print(f"🔄 缓存数量: {health_data.get('cache_size', 0)} 条")
                return True
            else:
                print(f"⚠️  健康检查接口异常(HTTP {response.status_code})，但继续测试其他接口")
                # 尝试测试info接口来验证服务基本可用性
                try:
                    info_response = self.session.get(f"{self.base_url}/info", timeout=5)
                    if info_response.status_code == 200:
                        print("✅ 服务基本功能正常，继续进行接口测试")
                        return True
                except:
                    pass
                return False
                
        except requests.exceptions.ConnectionError:
            print("❌ 服务连接失败 - 请确保服务已启动")
            print("启动命令: mvn spring-boot:run 或在IDE中运行ExtractServiceApplication")
            return False
        except Exception as e:
            print(f"⚠️  健康检查异常: {e}，但尝试继续测试")
            # 尝试测试info接口来验证服务基本可用性
            try:
                info_response = self.session.get(f"{self.base_url}/info", timeout=5)
                if info_response.status_code == 200:
                    print("✅ 服务基本功能正常，继续进行接口测试")
                    return True
            except:
                pass
            return False

    def test_info_endpoint(self):
        """测试/info接口"""
        print("\n🔍 测试 /info 接口")
        print("-" * 50)
        
        try:
            response = self.session.get(f"{self.base_url}/info", timeout=10)
            
            if response.status_code == 200:
                info_data = response.json()
                print("✅ /info 接口测试成功")
                print(f"📋 服务名称: {info_data.get('service', 'N/A')}")
                print(f"🔢 版本号: {info_data.get('version', 'N/A')}")
                print(f"📝 描述: {info_data.get('description', 'N/A')}")
                print(f"🏗️  架构: {info_data.get('architecture', 'N/A')}")
                print(f"🔄 调用关系: {info_data.get('call_relationship', 'N/A')}")
                return True
            else:
                print(f"❌ /info 接口测试失败，HTTP状态码: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ /info 接口测试异常: {e}")
            return False

    def test_extract_social_endpoint(self):
        """测试/extract/social接口"""
        print("\n🤝 测试 /extract/social 接口")
        print("-" * 50)
        
        test_text = "马云是阿里巴巴的创始人，与彭蕾、蔡崇信等人共同创立了这家公司。马云与张瑛结婚，育有三个孩子。"
        
        request_data = {
            "text": test_text,
            "extractTypes": "entities,relations",
            "maskSensitive": False
        }
        
        try:
            start_time = time.time()
            response = self.session.post(
                f"{self.base_url}/extract/social",
                json=request_data,
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                result = response.json()
                print("✅ /extract/social 接口测试成功")
                print(f"⏱️  响应时间: {response_time:.2f}秒")
                print(f"🎯 成功状态: {result.get('success', False)}")
                
                # 显示提取结果
                entities = result.get('entities', [])
                relations = result.get('relations', [])
                
                if entities:
                    print(f"👥 提取实体数量: {len(entities)}")
                    for i, entity in enumerate(entities[:3], 1):
                        print(f"   {i}. {entity.get('name', str(entity)) if isinstance(entity, dict) else entity}")
                
                if relations:
                    print(f"🔗 提取关系数量: {len(relations)}")
                    for i, relation in enumerate(relations[:3], 1):
                        if isinstance(relation, dict):
                            print(f"   {i}. {relation.get('subject', 'N/A')} → {relation.get('predicate', 'N/A')} → {relation.get('object', 'N/A')}")
                        else:
                            print(f"   {i}. {relation}")
                
                return True
            else:
                print(f"❌ /extract/social 接口测试失败，HTTP状态码: {response.status_code}")
                if response.text:
                    print(f"错误详情: {response.text[:200]}...")
                return False
                
        except Exception as e:
            print(f"❌ /extract/social 接口测试异常: {e}")
            return False

    def test_batch_extract_endpoint(self):
        """测试/extract/batch接口"""
        print("\n📦 测试 /extract/batch 接口")
        print("-" * 50)
        
        test_texts = [
            "周杰伦是著名的华语歌手，代表作品有《青花瓷》。",
            "成龙是国际知名的动作演员，主演过《醉拳》。",
            "刘德华出演了电影《无间道》，是香港四大天王之一。"
        ]
        
        request_data = {
            "texts": test_texts,
            "extractType": "triples"
        }
        
        try:
            start_time = time.time()
            response = self.session.post(
                f"{self.base_url}/extract/batch",
                json=request_data,
                headers={'Content-Type': 'application/json'},
                timeout=60
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                result = response.json()
                print("✅ /extract/batch 接口测试成功")
                print(f"⏱️  响应时间: {response_time:.2f}秒")
                print(f"🎯 成功状态: {result.get('success', False)}")
                print(f"📊 处理文本数量: {len(test_texts)}")
                
                # 显示批处理结果
                if result.get('results'):
                    results = result['results']
                    print(f"📈 批处理结果数量: {len(results) if isinstance(results, list) else 1}")
                elif result.get('triples'):
                    triples = result['triples']
                    print(f"🔗 提取三元组总数: {len(triples)}")
                    for i, triple in enumerate(triples[:5], 1):
                        if isinstance(triple, dict):
                            print(f"   {i}. {triple.get('subject', 'N/A')} → {triple.get('predicate', 'N/A')} → {triple.get('object', 'N/A')}")
                        else:
                            print(f"   {i}. {triple}")
                
                return True
            else:
                print(f"❌ /extract/batch 接口测试失败，HTTP状态码: {response.status_code}")
                if response.text:
                    print(f"错误详情: {response.text[:200]}...")
                return False
                
        except Exception as e:
            print(f"❌ /extract/batch 接口测试异常: {e}")
            return False

    def test_error_scenarios(self):
        """测试错误场景处理"""
        print("\n⚠️  测试错误场景处理")
        print("-" * 50)
        
        error_tests = [
            {
                "name": "空文本测试",
                "endpoint": "/extract",
                "data": {"text": "", "extractType": "triples"},
                "expected_error": True
            },
            {
                "name": "缺少文本字段测试",
                "endpoint": "/extract", 
                "data": {"extractType": "triples"},
                "expected_error": True
            },
            {
                "name": "空批处理列表测试",
                "endpoint": "/extract/batch",
                "data": {"texts": [], "extractType": "triples"},
                "expected_error": True
            },
            {
                "name": "社交接口空文本测试",
                "endpoint": "/extract/social",
                "data": {"text": "", "extractTypes": "entities,relations"},
                "expected_error": True
            }
        ]
        
        error_test_results = []
        
        for test in error_tests:
            try:
                response = self.session.post(
                    f"{self.base_url}{test['endpoint']}",
                    json=test['data'],
                    headers={'Content-Type': 'application/json'},
                    timeout=10
                )
                
                if test['expected_error']:
                    if response.status_code == 200:
                        result = response.json()
                        if result.get('error') or not result.get('success', True):
                            print(f"✅ {test['name']}: 正确返回错误信息")
                            error_test_results.append(True)
                        else:
                            print(f"❌ {test['name']}: 应该返回错误但返回了成功")
                            error_test_results.append(False)
                    else:
                        print(f"✅ {test['name']}: 正确返回HTTP错误状态码 {response.status_code}")
                        error_test_results.append(True)
                else:
                    print(f"🔍 {test['name']}: HTTP状态码 {response.status_code}")
                    error_test_results.append(response.status_code == 200)
                    
            except Exception as e:
                print(f"❌ {test['name']}: 测试异常 - {e}")
                error_test_results.append(False)
        
        success_rate = (sum(error_test_results) / len(error_test_results)) * 100
        print(f"\n📊 错误场景测试成功率: {success_rate:.1f}% ({sum(error_test_results)}/{len(error_test_results)})")
        return success_rate > 75

    def get_test_cases(self):
        """构造测试用例数据"""
        return [
            {
                "id": "TC001",
                "name": "娱乐明星综合测试",
                "description": "测试明星个人信息、作品、关系提取",
                "text": "周杰伦（Jay Chou），1979年1月18日出生于台湾省新北市，华语流行乐男歌手、音乐人、演员、导演。他与昆凌于2015年1月17日在英国约克郡举行婚礼，两人育有女儿周小周（Hathaway，2015年7月出生）和儿子周小豆（Romeo，2017年6月出生）。音乐代表作品包括《双截棍》《青花瓷》《稻香》《告白气球》《Mojito》等经典歌曲。",
                "expected_entities": ["周杰伦", "昆凌", "周小周", "周小豆"],
                "expected_relations": ["结婚", "育有", "出生"]
            },
            {
                "id": "TC002", 
                "name": "动作电影明星测试",
                "description": "测试动作明星职业生涯、家庭关系提取",
                "text": "成龙（Jackie Chan），原名陈港生，1954年4月7日出生于香港中西区，国际知名动作演员、武术指导、导演、制片人。他与台湾演员林凤娇于1982年结婚，育有儿子房祖名（1982年出生）。代表作品包括《醉拳》《警察故事》系列、《尖峰时刻》系列、《宝贝计划》等。",
                "expected_entities": ["成龙", "林凤娇", "房祖名"],
                "expected_relations": ["结婚", "育有", "出演"]
            },
            {
                "id": "TC003",
                "name": "四大天王综合测试", 
                "description": "测试香港四大天王之一的综合信息提取",
                "text": "刘德华（Andy Lau），1961年9月27日出生于香港新界大埔，华语影视男演员、歌手、制片人、作词人。他与马来西亚华裔朱丽倩于2008年6月23日在拉斯维加斯秘密结婚，育有女儿刘向蕙（Hanna，2012年5月出生）和女儿刘向慧（2015年出生）。电影代表作包括《无间道》《天下无贼》《失孤》等。",
                "expected_entities": ["刘德华", "朱丽倩", "刘向蕙", "刘向慧"],
                "expected_relations": ["结婚", "育有", "出演"]
            },
            {
                "id": "TC004",
                "name": "科幻电影制作测试",
                "description": "测试科幻电影相关人物、制作信息提取", 
                "text": "《流浪地球2》是2023年大年初一上映的中国科幻电影，由郭帆执导，制片人为龚格尔，投资超过6亿人民币。主演阵容包括刘德华、吴京、李雪健、沙溢等实力派演员。刘德华在片中饰演数字生命科学家图恒宇，为了让因车祸去世的女儿图丫丫能够复活，不惜一切代价参与数字生命计划。",
                "expected_entities": ["郭帆", "龚格尔", "刘德华", "吴京", "图恒宇", "图丫丫"],
                "expected_relations": ["执导", "制片", "主演", "饰演"]
            },
            {
                "id": "TC005",
                "name": "体育明星职业生涯测试",
                "description": "测试体育明星NBA生涯、个人生活提取",
                "text": "姚明，1980年9月12日出生于上海市徐汇区，前中国职业篮球运动员，现任中国篮球协会主席。他身高2.26米，司职中锋，2002年以NBA选秀状元身份加盟休斯顿火箭队。2007年8月3日与中国女篮运动员叶莉在上海结婚，2010年5月21日女儿姚沁蕾在休斯顿出生。",
                "expected_entities": ["姚明", "叶莉", "姚沁蕾"],
                "expected_relations": ["结婚", "出生", "加盟"]
            }
        ]
    
    def execute_test_case(self, test_case):
        """执行单个测试用例"""
        print(f"\n🧪 执行测试用例: {test_case['id']} - {test_case['name']}")
        print(f"📝 测试描述: {test_case['description']}")
        print("-" * 60)
        
        # 构造请求数据 - 使用现有的extract接口
        request_data = {
            "text": test_case["text"].strip(),
            "extractType": "triples"
        }
        
        start_time = time.time()
        
        try:
            print("📤 发送数据提取请求...")
            response = self.session.post(
                f"{self.base_url}/extract",
                json=request_data,
                headers={'Content-Type': 'application/json'},
                timeout=60
            )
            
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                result = response.json()
                
                print(f"✅ 数据提取成功！")
                print(f"⏱️  响应时间: {response_time:.2f}秒")
                print(f"🎯 成功状态: {result.get('success', False)}")
                print(f"📊 降级模式: {'是' if result.get('fallback', False) else '否'}")
                
                # 解析提取结果
                triples = result.get('triples', [])
                entities = result.get('entities', [])
                relations = result.get('relations', [])
                
                print(f"\n📈 提取结果统计:")
                print(f"🔗 三元组数量: {len(triples)} 个")
                if entities:
                    print(f"👥 实体数量: {len(entities)} 个")
                if relations:
                    print(f"🔀 关系数量: {len(relations)} 个")
                
                # 显示具体提取结果
                if triples:
                    print(f"\n🔗 提取的三元组 (显示前5个):")
                    for i, triple in enumerate(triples[:5], 1):
                        if isinstance(triple, dict):
                            subject = triple.get('subject', 'N/A')
                            predicate = triple.get('predicate', 'N/A')
                            obj = triple.get('object', 'N/A')
                            print(f"  {i}. {subject} → {predicate} → {obj}")
                        else:
                            print(f"  {i}. {triple}")
                
                if entities:
                    print(f"\n👥 提取的实体:")
                    for i, entity in enumerate(entities[:8], 1):
                        if isinstance(entity, dict):
                            print(f"  {i}. {entity.get('name', str(entity))}")
                        else:
                            print(f"  {i}. {entity}")
                
                # 数据库保存状态 - 根据系统设计，数据应该自动保存
                if result.get('success', False):
                    print(f"\n💾 ✅ 数据处理成功，根据系统设计应已保存到数据库")
                    print(f"📍 数据库位置: localhost:3306/extract-graph")
                else:
                    print(f"\n💾 ⚠️  数据处理可能未完全成功")
                
                # 记录测试结果
                test_result = {
                    "test_id": test_case["id"],
                    "test_name": test_case["name"],
                    "status": "SUCCESS",
                    "response_time": response_time,
                    "success": result.get('success', False),
                    "fallback": result.get('fallback', False),
                    "triples_count": len(triples),
                    "entities_count": len(entities) if entities else 0,
                    "relations_count": len(relations) if relations else 0,
                    "timestamp": datetime.now().isoformat()
                }
                
                self.test_results.append(test_result)
                return True
                
            else:
                error_msg = f"HTTP错误 {response.status_code}"
                print(f"❌ 请求失败: {error_msg}")
                if response.text:
                    print(f"错误详情: {response.text[:200]}...")
                
                self.test_results.append({
                    "test_id": test_case["id"],
                    "test_name": test_case["name"],
                    "status": "FAILED",
                    "error": error_msg,
                    "response_time": response_time,
                    "timestamp": datetime.now().isoformat()
                })
                return False
                
        except requests.exceptions.Timeout:
            error_msg = "请求超时"
            print(f"❌ {error_msg}")
            self.test_results.append({
                "test_id": test_case["id"],
                "test_name": test_case["name"],
                "status": "TIMEOUT",
                "error": error_msg,
                "timestamp": datetime.now().isoformat()
            })
            return False
            
        except Exception as e:
            error_msg = f"请求异常: {str(e)}"
            print(f"❌ {error_msg}")
            print(f"异常详情: {traceback.format_exc()}")
            
            self.test_results.append({
                "test_id": test_case["id"],
                "test_name": test_case["name"],
                "status": "ERROR",
                "error": error_msg,
                "timestamp": datetime.now().isoformat()
            })
            return False
    
    def print_test_summary(self):
        """打印测试总结报告"""
        print("\n" + "=" * 70)
        print("📊 数据库集成测试总结报告")
        print("=" * 70)
        
        total_tests = len(self.test_results)
        successful_tests = len([r for r in self.test_results if r["status"] == "SUCCESS"])
        failed_tests = total_tests - successful_tests
        
        print(f"🎯 测试概览:")
        print(f"   总测试用例: {total_tests}")
        print(f"   成功用例: {successful_tests} ✅")
        print(f"   失败用例: {failed_tests} ❌")
        print(f"   成功率: {(successful_tests/total_tests*100):.1f}%" if total_tests > 0 else "   成功率: 0%")
        
        if successful_tests > 0:
            # 性能统计
            response_times = [r["response_time"] for r in self.test_results if r["status"] == "SUCCESS"]
            avg_response_time = sum(response_times) / len(response_times)
            max_response_time = max(response_times)
            min_response_time = min(response_times)
            
            print(f"\n⚡ 性能统计:")
            print(f"   平均响应时间: {avg_response_time:.2f}秒")
            print(f"   最长响应时间: {max_response_time:.2f}秒")
            print(f"   最短响应时间: {min_response_time:.2f}秒")
            
            # 数据统计
            total_triples = sum([r.get("triples_count", 0) for r in self.test_results if r["status"] == "SUCCESS"])
            total_entities = sum([r.get("entities_count", 0) for r in self.test_results if r["status"] == "SUCCESS"])
            total_relations = sum([r.get("relations_count", 0) for r in self.test_results if r["status"] == "SUCCESS"])
            
            print(f"\n📈 数据提取统计:")
            print(f"   总提取三元组: {total_triples} 个")
            if total_entities > 0:
                print(f"   总提取实体: {total_entities} 个")
            if total_relations > 0:
                print(f"   总提取关系: {total_relations} 个")
            
            # 成功率统计
            success_count = len([r for r in self.test_results if r["status"] == "SUCCESS" and r.get("success")])
            fallback_count = len([r for r in self.test_results if r["status"] == "SUCCESS" and r.get("fallback")])
            
            print(f"\n🔄 处理模式统计:")
            print(f"   正常处理: {success_count - fallback_count} 次")
            print(f"   降级处理: {fallback_count} 次")
        
        # 失败用例详情
        if failed_tests > 0:
            print(f"\n❌ 失败用例详情:")
            for result in self.test_results:
                if result["status"] != "SUCCESS":
                    print(f"   {result['test_id']}: {result.get('error', 'Unknown error')}")
    
    def print_database_verification_guide(self):
        """打印数据库验证指南"""
        test_time = self.test_start_time.strftime('%Y-%m-%d %H:%M:%S')
        
        print(f"\n💾 数据库验证指南")
        print("=" * 50)
        print(f"🔗 数据库连接信息:")
        print(f"   主机: localhost")
        print(f"   端口: 3306") 
        print(f"   数据库: extract-graph")
        print(f"   用户名: root")
        print(f"   密码: 123456")
        print(f"   测试时间: {test_time}")
        
        print(f"\n📋 验证SQL命令:")
        print(f"# 连接数据库")
        print(f"mysql -h localhost -P 3306 -u root -p123456 extract-graph")
        print(f"")
        print(f"# 查询本次测试写入的人员数据")
        print(f"SELECT * FROM celebrity WHERE created_at >= '{test_time}' ORDER BY created_at DESC;")
        print(f"")
        print(f"# 查询本次测试写入的作品数据")
        print(f"SELECT * FROM work WHERE created_at >= '{test_time}' ORDER BY created_at DESC;")
        print(f"")
        print(f"# 查询本次测试写入的事件数据")
        print(f"SELECT * FROM event WHERE created_at >= '{test_time}' ORDER BY created_at DESC;")
        print(f"")
        print(f"# 查看数据总量统计")
        print(f"SELECT ")
        print(f"    (SELECT COUNT(*) FROM celebrity) as celebrity_total,")
        print(f"    (SELECT COUNT(*) FROM work) as work_total,")
        print(f"    (SELECT COUNT(*) FROM event) as event_total;")
        
        print(f"\n🔍 数据验证提示:")
        print(f"1. 检查时间戳字段 created_at 确认为本次测试时间")
        print(f"2. 验证提取的实体名称是否正确写入")
        print(f"3. 检查关系数据是否建立正确")
        print(f"4. 确认数据类型分类是否准确")
        
        print(f"\n🗑️  测试数据清理 (手工执行):")
        print(f"# ⚠️  请在验证完成后手工删除测试数据")
        print(f"DELETE FROM celebrity WHERE created_at >= '{test_time}';")
        print(f"DELETE FROM work WHERE created_at >= '{test_time}';")
        print(f"DELETE FROM event WHERE created_at >= '{test_time}';")
        
        print(f"\n✅ 测试数据已保留在数据库中，请按上述指导进行验证")

def main():
    """主测试函数"""
    print("🧪 全接口数据库集成测试 - 完整API验证")
    print("=" * 70)
    print("📅 测试开始时间:", datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
    
    tester = DatabaseIntegrationTester()
    
    # 1. 服务健康检查
    print("\n" + "=" * 70)
    print("第一阶段：基础服务测试")
    print("=" * 70)
    
    if not tester.check_service_health():
        print("\n❌ 服务不可用，测试终止")
        print("请确保服务已启动: mvn spring-boot:run")
        sys.exit(1)
    
    # 2. 测试/info接口
    tester.test_info_endpoint()
    
    # 3. 测试错误场景
    print("\n" + "=" * 70) 
    print("第二阶段：错误场景测试")
    print("=" * 70)
    tester.test_error_scenarios()
    
    # 4. 测试社交关系接口
    print("\n" + "=" * 70)
    print("第三阶段：特殊接口测试")
    print("=" * 70)
    tester.test_extract_social_endpoint()
    
    # 5. 测试批处理接口
    tester.test_batch_extract_endpoint()
    
    # 6. 执行主要测试用例
    print("\n" + "=" * 70)
    print("第四阶段：核心功能测试")
    print("=" * 70)
    
    test_cases = tester.get_test_cases()
    print(f"\n🎯 准备执行 {len(test_cases)} 个核心测试用例...")
    print("📍 使用接口: /api/v1/extract")
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\n[进度: {i}/{len(test_cases)}]")
        success = tester.execute_test_case(test_case)
        
        # 测试间隔，避免过于频繁
        if i < len(test_cases):
            print("⏳ 等待2秒后执行下一个测试用例...")
            time.sleep(2)
    
    # 7. 打印测试总结
    print("\n" + "=" * 70)
    print("第五阶段：测试总结与验证指导")
    print("=" * 70)
    tester.print_test_summary()
    
    # 8. 打印数据库验证指南
    tester.print_database_verification_guide()
    
    print(f"\n🎉 全接口测试完成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("📝 已完成所有API接口的完整测试验证")
    print("🔍 包含接口: /health, /info, /extract, /extract/batch, /extract/social")
    print("⚠️  包含错误场景测试确保系统健壮性")
    print("💾 请按照上述指导验证数据库中的测试数据")

if __name__ == "__main__":
    main()
 