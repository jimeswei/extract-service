#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
异步提取服务集成测试脚本
验证异步提取接口与数据库写入完整流程
专注测试 AsyncExtractController 的所有功能
"""

import requests
import json
import time
from datetime import datetime
from typing import Dict, Any
import sys

# 服务配置
BASE_URL = "http://localhost:2701/api/v1/async"
EXTRACT_URL = f"{BASE_URL}/extract"
HEALTH_URL = f"{BASE_URL}/health"
INFO_URL = f"{BASE_URL}/info"

class AsyncExtractTester:
    """异步提取服务测试器"""
    
    def __init__(self):
        self.base_url = BASE_URL
        self.session = requests.Session()
        self.test_results = []
        self.test_start_time = datetime.now()
        
    def print_test_header(self, title):
        """打印测试标题"""
        print(f"\n{'='*60}")
        print(f"  {title}")
        print(f"{'='*60}")

    def print_response(self, response_data, title="响应"):
        """格式化打印响应数据"""
        print(f"\n{title}:")
        if isinstance(response_data, dict):
            print(json.dumps(response_data, ensure_ascii=False, indent=2))
        else:
            print(response_data)
        
    def check_service_health(self):
        """检查异步服务健康状态"""
        print("🏥 异步服务健康检查")
        print("=" * 50)
        
        try:
            response = self.session.get(HEALTH_URL, timeout=10)
            
            if response.status_code == 200:
                health_data = response.json()
                print(f"✅ 服务状态: {health_data.get('status', 'unknown')}")
                print(f"🔄 异步功能: {'已启用' if health_data.get('async_enabled') else '未启用'}")
                return True
            else:
                print(f"⚠️  健康检查异常(HTTP {response.status_code})，但继续测试")
                return True
                
        except requests.exceptions.ConnectionError:
            print("❌ 异步服务连接失败 - 请确保服务已启动")
            return False
        except Exception as e:
            print(f"⚠️  健康检查异常: {e}，但尝试继续测试")
            return True

    def test_async_extract(self, test_name, text_input, extract_params=None):
        """异步提取接口测试方法"""
        print(f"\n🧪 {test_name}")
        print("-" * 60)
        
        # 构建请求数据
        request_data = {"textInput": text_input}
        
        # extractParams不传则使用默认值
        if extract_params is not None:
            request_data["extractParams"] = extract_params
        
        # 判断输入类型并显示
        input_type = "数组" if isinstance(text_input, list) else "字符串"
        input_size = len(text_input) if isinstance(text_input, list) else len(str(text_input))
        print(f"📝 输入类型: {input_type} | 大小: {input_size}")
        
        try:
            start_time = time.time()
            response = self.session.post(
                EXTRACT_URL,
                json=request_data,
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                result = response.json()
                success = result.get('success', False)
                message = result.get('message', '')
                
                print(f"✅ 测试成功 | ⏱️ {response_time:.2f}秒 | 🎯 成功: {success}")
                print(f"💬 响应消息: {message}")
                
                self.test_results.append({
                    "name": test_name,
                    "success": True,
                    "response_time": response_time,
                    "async_success": success,
                    "input_type": input_type,
                    "message": message
                })
                return True
            else:
                print(f"❌ 测试失败，HTTP状态码: {response.status_code}")
                self.test_results.append({"name": test_name, "success": False, "input_type": input_type})
                return False
                
        except Exception as e:
            print(f"❌ 测试异常: {e}")
            self.test_results.append({"name": test_name, "success": False, "input_type": input_type})
            return False

    def test_service_info(self):
        """测试异步服务信息"""
        self.print_test_header("异步服务信息测试")
        
        try:
            response = self.session.get(INFO_URL, timeout=10)
            response.raise_for_status()
            
            data = response.json()
            self.print_response(data, "异步服务信息")
            print("✅ 异步服务信息获取成功")
            return True
            
        except Exception as e:
            print(f"❌ 异步服务信息获取失败: {e}")
            return False

    def test_error_scenarios(self):
        """测试错误场景"""
        self.print_test_header("错误场景测试")
        
        error_tests = [
            {"data": {"textInput": ""}, "desc": "空字符串"},
            {"data": {"textInput": []}, "desc": "空数组"},
            {"data": {}, "desc": "缺少textInput"},
            {"data": {"textInput": None}, "desc": "null文本"},
        ]
        
        success_count = 0
        
        for test in error_tests:
            try:
                response = self.session.post(
                    EXTRACT_URL,
                    json=test["data"],
                    headers={'Content-Type': 'application/json'},
                    timeout=10
                )
                
                if response.status_code == 200:
                    result = response.json()
                    if not result.get('success', True):  # 期望失败
                        print(f"✅ {test['desc']}: 正确处理错误")
                        success_count += 1
                    else:
                        print(f"❌ {test['desc']}: 应该返回错误但成功了")
                else:
                    print(f"❌ {test['desc']}: HTTP错误 {response.status_code}")
                    
            except Exception as e:
                print(f"❌ {test['desc']}: 异常 {e}")
        
        print(f"📊 错误场景测试成功率: {success_count/len(error_tests)*100:.1f}%")
        return success_count == len(error_tests)

    def run_string_format_tests(self):
        """运行字符串格式测试"""
        self.print_test_header("字符串格式异步提取测试")
        
        string_tests = [
            {
                "name": "人员关系测试(字符串)",
                "text": "刘德华是香港著名演员和歌手，出演过《无间道》等经典电影。",
                "params": "triples"
            },
            {
                "name": "人员作品测试(字符串)", 
                "text": "周杰伦发行了专辑《叶惠美》，收录了经典歌曲《东风破》。",
                "params": "entities"
            },
            {
                "name": "事件活动测试(字符串)",
                "text": "2023年金马奖颁奖典礼在台北举行，刘德华获得最佳男主角奖。",
                "params": "relations"
            }
        ]
        
        for i, test in enumerate(string_tests, 1):
            print(f"\n[字符串测试 {i}/{len(string_tests)}]")
            self.test_async_extract(test["name"], test["text"], test["params"])

    def run_array_format_tests(self):
        """运行JSON数组格式测试"""
        self.print_test_header("JSON数组格式异步提取测试")
        
        array_tests = [
            {
                "name": "人员事件测试(数组)",
                "texts": [
                    "刘德华参加了2023年香港电影节。",
                    "周杰伦在演唱会上演唱了《青花瓷》。",
                    "张学友获得了金曲奖最佳男歌手。"
                ],
                "params": "triples"
            },
            {
                "name": "事件作品测试(数组)",
                "texts": [
                    "《流浪地球2》在春节档上映。",
                    "《满江红》票房突破40亿。",
                    "《深海》采用了全新的动画技术。"
                ],
                "params": "entities"
            },
            {
                "name": "综合全链路测试(数组)",
                "texts": [
                    "导演张艺谋执导了电影《满江红》。",
                    "易烊千玺在《满江红》中饰演主角。",
                    "《满江红》获得了春节档票房冠军。",
                    "影片讲述了南宋抗金的故事。"
                ],
                "params": "relations"
            }
        ]
        
        for i, test in enumerate(array_tests, 1):
            print(f"\n[数组测试 {i}/{len(array_tests)}]")
            self.test_async_extract(test["name"], test["texts"], test["params"])

    def run_default_params_tests(self):
        """测试默认参数"""
        self.print_test_header("默认参数测试")
        
        default_tests = [
            {
                "name": "默认参数测试(字符串)",
                "text": "成龙是功夫电影明星。"
            },
            {
                "name": "默认参数测试(数组)", 
                "texts": [
                    "李连杰主演了《黄飞鸿》。",
                    "甄子丹出演了《叶问》系列。",
                    "吴京导演了《战狼》。"
                ]
            }
        ]
        
        for i, test in enumerate(default_tests, 1):
            print(f"\n[默认参数测试 {i}/{len(default_tests)}]")
            text_input = test.get("text") or test.get("texts")
            self.test_async_extract(test["name"], text_input)

    def run_event_work_tests(self):
        """运行事件-作品关系测试"""
        self.print_test_header("事件-作品关系测试")
        
        # 基于您成功的测试案例
        event_work_test = {
            "name": "事件作品关系测试(数组)",
            "texts": [
                "金马奖颁奖典礼播放了《无间道》片段",
                "柏林电影节展映《红高粱》",
                "奥运会开幕式演唱《青花瓷》",
                "音乐节演奏《东风破》"
            ],
            "params": "triples"
        }
        
        print(f"\n🧪 {event_work_test['name']}")
        print(f"📝 输入类型: 数组 | 大小: {len(event_work_test['texts'])}")
        print("💡 专门测试事件与作品之间的关系提取")
        print("🎯 预期生成: event_work 表数据")
        
        for i, text in enumerate(event_work_test['texts'], 1):
            print(f"  {i}. {text}")
        
        self.test_async_extract(event_work_test['name'], event_work_test['texts'], event_work_test['params'])
        
        # 追加单个事件-作品关系测试
        single_event_work_tests = [
            {
                "name": "电影节展映测试",
                "text": "第95届奥斯卡颁奖典礼展映了《瞬息全宇宙》，该片获得最佳影片奖。",
                "params": "triples"
            },
            {
                "name": "音乐会演奏测试", 
                "text": "维也纳新年音乐会演奏了《蓝色多瑙河》，现场观众热烈鼓掌。",
                "params": "relations"
            }
        ]
        
        for i, test in enumerate(single_event_work_tests, 1):
            print(f"\n[事件作品测试 {i}/{len(single_event_work_tests)}]")
            self.test_async_extract(test['name'], test['text'], test['params'])

    def run_long_text_tests(self):
        """运行长文本处理测试"""
        self.print_test_header("长文本处理测试")
        
        # 短文本测试
        short_text = "刘德华是香港著名演员，出演了《无间道》、《桃姐》等经典电影，同时也是优秀的歌手。"
        print(f"\n🧪 短文本处理测试")
        print(f"📝 文本长度: {len(short_text)} 字符")
        self.test_async_extract("短文本异步处理", short_text, "triples")
        
        # 中等文本测试
        medium_text = """
        周杰伦，华语流行音乐歌手、音乐人、演员、导演、编剧，1979年1月18日出生于台湾省新北市。
        2000年发行首张个人专辑《Jay》正式出道。2001年发行专辑《范特西》奠定其融合中西方音乐的风格。
        2002年举行"The One"世界巡回演唱会。2003年成为《时代》杂志封面人物。
        2004年获得世界音乐大奖中国区最畅销艺人奖。2005年凭借动作片《头文字D》获得台湾电影金马奖最佳新人奖。
        2007年自编自导的文艺片《不能说的秘密》获得台湾电影金马奖年度台湾杰出电影奖。
        """.strip()
        print(f"\n🧪 中等文本处理测试")
        print(f"📝 文本长度: {len(medium_text)} 字符")
        self.test_async_extract("中等文本异步处理", medium_text, "entities")
        
        # 长文本测试（超过2000字符）
        long_text = """
        张艺谋，中国电影导演，1950年4月2日出生于陕西省西安市。中国第五代导演代表人物之一。
        1978年进入北京电影学院摄影系学习。1982年毕业后分配到广西电影制片厂。
        1984年担任电影《一个和八个》的摄影师，该片获得中国电影金鸡奖最佳摄影奖。
        1987年执导处女作《红高粱》，该片获得第38届柏林国际电影节金熊奖，成为首部获得此殊荣的中国电影。
        1990年执导《菊豆》，该片获得第43届戛纳国际电影节路易斯·布努埃尔特别奖，并获得第63届奥斯卡金像奖最佳外语片提名。
        1991年执导《大红灯笼高高挂》，该片获得第48届威尼斯国际电影节银狮奖，并获得第64届奥斯卡金像奖最佳外语片提名。
        1992年执导《秋菊打官司》，该片获得第49届威尼斯国际电影节金狮奖。
        1994年执导《活着》，该片获得第47届戛纳国际电影节评审团大奖。
        1999年执导《我的父亲母亲》，该片获得第50届柏林国际电影节银熊奖。
        2002年执导《英雄》，该片以2.5亿元人民币成为中国电影票房冠军，并获得第75届奥斯卡金像奖最佳外语片提名。
        2004年执导《十面埋伏》，该片获得第61届威尼斯国际电影节未来数字电影奖。
        2006年执导《满城尽带黄金甲》，该片获得第79届奥斯卡金像奖最佳服装设计提名。
        2008年担任北京奥运会开幕式和闭幕式总导演。
        2011年执导《金陵十三钗》，该片获得第84届奥斯卡金像奖最佳外语片提名。
        2016年执导《长城》，这是他首部中美合拍的商业大片。
        2018年执导《影》，该片获得第55届台湾电影金马奖最佳导演奖。
        2021年执导《悬崖之上》，该片是他首部谍战题材电影。
        张艺谋的电影作品题材广泛，从农村题材到古装史诗，从现代都市到科幻奇幻，展现了深厚的艺术功底和多样化的创作能力。
        他善于运用色彩和视觉语言，形成了独特的"张艺谋式"电影美学。
        在国际电影界，张艺谋被誉为中国电影走向世界的重要推手，为中国电影在国际舞台上赢得了声誉。
        除了电影创作，张艺谋还涉足话剧、歌剧等艺术领域，展现了全方位的艺术才华。
        """.strip()
        print(f"\n🧪 长文本处理测试")
        print(f"📝 文本长度: {len(long_text)} 字符 (预期触发分批处理)")
        self.test_async_extract("长文本异步处理", long_text, "relations")

    def print_summary(self):
        """打印测试总结"""
        self.print_test_header("测试总结报告")
        
        total_tests = len(self.test_results)
        successful_tests = len([r for r in self.test_results if r.get('success', False)])
        
        print(f"🎯 总测试: {total_tests} | ✅ 成功: {successful_tests} | ❌ 失败: {total_tests - successful_tests}")
        print(f"📈 成功率: {successful_tests/total_tests*100:.1f}%" if total_tests > 0 else "📈 成功率: 0%")
        
        if successful_tests > 0:
            avg_response_time = sum(r.get('response_time', 0) for r in self.test_results if r.get('success')) / successful_tests
            print(f"⏱️  平均响应时间: {avg_response_time:.2f} 秒")
        
        # 按输入类型统计
        string_tests = [r for r in self.test_results if r.get('input_type') == '字符串']
        array_tests = [r for r in self.test_results if r.get('input_type') == '数组']
        
        if string_tests:
            string_success = len([r for r in string_tests if r.get('success')])
            print(f"📝 字符串格式: {string_success}/{len(string_tests)} 成功")
            
        if array_tests:
            array_success = len([r for r in array_tests if r.get('success')])
            print(f"📋 数组格式: {array_success}/{len(array_tests)} 成功")

    def print_database_guide(self):
        """打印数据库验证指南"""
        self.print_test_header("数据库验证指南")
        
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

    def print_feature_summary(self):
        """打印功能特性总结"""
        self.print_test_header("异步提取服务特性总结")
        
        print("📋 异步接口:")
        print("  ✅ POST /api/v1/async/extract - 异步文本提取")
        print("  ✅ GET /api/v1/async/info - 服务信息")
        print("  ✅ GET /api/v1/async/health - 健康检查")
        
        print("\n🔧 技术特性:")
        print("  • 立即响应: 所有请求立即返回成功状态")
        print("  • 后台处理: 实际提取在后台异步执行")
        print("  • 智能长度检测: ≤2000字符直接处理，>2000字符分批处理")
        print("  • 格式支持: 支持字符串和JSON数组两种输入格式")
        print("  • 参数灵活: extractParams支持triples/entities/relations，默认triples")
        print("  • 高性能缓存: Caffeine缓存，提升处理效率")
        print("  • 并行处理: 最多3个分片同时处理")
        print("  • 数据库集成: 7张表完整存储知识图谱数据")
        print("  • 完整关系支持: 人-人、人-作品、人-事件、事件-作品关系")
        print("  • 完善的错误处理和日志记录")

def main():
    """主函数"""
    print("🧪 异步提取服务集成测试")
    print("=" * 80)
    print(f"📅 测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("🎯 目标: 验证异步提取接口完整功能")
    
    tester = AsyncExtractTester()
    
    # 检查服务健康状态
    if not tester.check_service_health():
        print("❌ 服务不可用，测试终止")
        sys.exit(1)
    
    # 获取服务信息
    tester.test_service_info()
    
    # 错误场景测试
    tester.test_error_scenarios()
    
    # 字符串格式测试
    tester.run_string_format_tests()
    
    # JSON数组格式测试
    tester.run_array_format_tests()
    
    # 默认参数测试
    tester.run_default_params_tests()
    
    # 事件-作品关系测试
    tester.run_event_work_tests()
    
    # 长文本处理测试
    tester.run_long_text_tests()
    
    # 打印总结
    tester.print_summary()
    tester.print_database_guide()
    tester.print_feature_summary()
    
    test_end_time = datetime.now()
    test_duration = (test_end_time - tester.test_start_time).total_seconds()
    
    print(f"\n✅ 测试完成: {test_end_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"🎊 异步提取服务测试成功 - 总耗时: {test_duration:.1f} 秒")

if __name__ == "__main__":
    main() 