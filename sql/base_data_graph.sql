/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80404
 Source Host           : localhost:3306
 Source Schema         : base_data_graph

 Target Server Type    : MySQL
 Target Server Version : 80404
 File Encoding         : 65001

 Date: 26/06/2025 18:00:00 (v3.0 Knowledge Graph Enhanced)
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for celebrity (v3.0 Enhanced)
-- ----------------------------
DROP TABLE IF EXISTS `celebrity`;
CREATE TABLE `celebrity` (
  `id` int NOT NULL AUTO_INCREMENT,
  `celebrity_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'id',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `nationality` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '国籍（修正为规范表述）',
  `birthdate` varchar(50) DEFAULT NULL COMMENT '出生日期（用于动态计算年龄）',
  `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '性别',
  `profession` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '职业',
  `spouse` varchar(50) DEFAULT NULL COMMENT '配偶',
  `relatives` text COMMENT '亲属',
  `company` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '公司',
  `position` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '职位',
  `education` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '教育经历（结构化存储）',
  `resume` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '履历（结构化存储）',
  `baike` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '百科',
  `group` varchar(255) DEFAULT NULL,
  `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
  `version` int DEFAULT 1 COMMENT '版本号',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_confidence` (`confidence_score`)
) ENGINE=InnoDB AUTO_INCREMENT=2441 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员实体表 - v3.0知识图谱增强版';

-- ----------------------------
-- Table structure for celebrity_celebrity (v3.0 Enhanced)
-- ----------------------------
DROP TABLE IF EXISTS `celebrity_celebrity`;
CREATE TABLE `celebrity_celebrity` (
  `id` int NOT NULL AUTO_INCREMENT,
  `from_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源实体ID',
  `to_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标实体ID',
  `e_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '关系类型',
  `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
  `source_info` varchar(500) DEFAULT NULL COMMENT '来源信息',
  `version` int DEFAULT 1 COMMENT '版本号',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_from_to` (`from_id`, `to_id`),
  KEY `idx_confidence` (`confidence_score`)
) ENGINE=InnoDB AUTO_INCREMENT=6821 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员-人员关系表 - v3.0知识图谱增强版';

-- ----------------------------
-- Table structure for celebrity_event (v3.0 Enhanced)
-- ----------------------------
DROP TABLE IF EXISTS `celebrity_event`;
CREATE TABLE `celebrity_event` (
  `id` int NOT NULL AUTO_INCREMENT,
  `from_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源实体ID',
  `to_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标实体ID',
  `e_type` varchar(100) DEFAULT NULL COMMENT '关系类型',
  `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
  `source_info` varchar(500) DEFAULT NULL COMMENT '来源信息',
  `version` int DEFAULT 1 COMMENT '版本号',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_from_to` (`from_id`, `to_id`),
  KEY `idx_confidence` (`confidence_score`)
) ENGINE=InnoDB AUTO_INCREMENT=35071 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员-事件关系表 - v3.0知识图谱增强版';

-- ----------------------------
-- Table structure for celebrity_work (v3.0 Enhanced)
-- ----------------------------
DROP TABLE IF EXISTS `celebrity_work`;
CREATE TABLE `celebrity_work` (
  `id` int NOT NULL AUTO_INCREMENT,
  `from_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源实体ID',
  `to_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标实体ID',
  `e_type` varchar(100) DEFAULT NULL COMMENT '关系类型',
  `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
  `source_info` varchar(500) DEFAULT NULL COMMENT '来源信息',
  `version` int DEFAULT 1 COMMENT '版本号',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_from_to` (`from_id`, `to_id`),
  KEY `idx_confidence` (`confidence_score`)
) ENGINE=InnoDB AUTO_INCREMENT=6289 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员-作品关系表 - v3.0知识图谱增强版';

-- ----------------------------
-- Table structure for event (v3.0 Enhanced)
-- ----------------------------
DROP TABLE IF EXISTS `event`;
CREATE TABLE `event` (
  `id` int NOT NULL AUTO_INCREMENT,
  `event_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `event_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '活动名称',
  `event_type` enum('品牌代言','颁奖典礼','综艺录制','粉丝见面会','公益活动','其他') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `time` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开始时间',
  `group` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
  `version` int DEFAULT 1 COMMENT '版本号',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_event_name` (`event_name`),
  KEY `idx_confidence` (`confidence_score`)
) ENGINE=InnoDB AUTO_INCREMENT=9967 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件实体表 - v3.0知识图谱增强版';

-- ----------------------------
-- Table structure for event_work (v3.0 Enhanced)
-- ----------------------------
DROP TABLE IF EXISTS `event_work`;
CREATE TABLE `event_work` (
  `id` int NOT NULL AUTO_INCREMENT,
  `from_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源实体ID',
  `to_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标实体ID',
  `e_type` varchar(100) DEFAULT NULL COMMENT '关系类型',
  `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
  `source_info` varchar(500) DEFAULT NULL COMMENT '来源信息',
  `version` int DEFAULT 1 COMMENT '版本号',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_from_to` (`from_id`, `to_id`),
  KEY `idx_confidence` (`confidence_score`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事件-作品关系表 - v3.0知识图谱增强版';

-- ----------------------------
-- Table structure for work (v3.0 Enhanced)
-- ----------------------------
DROP TABLE IF EXISTS `work`;
CREATE TABLE `work` (
  `id` int NOT NULL AUTO_INCREMENT,
  `work_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作品名称',
  `work_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作品类型',
  `release_date` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布日期',
  `role` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '饰演角色/参与身份',
  `platform` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '播放平台',
  `awards` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '获奖记录（格式：{"年份":"奖项名称"}）',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '描述',
  `baike` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `group` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
  `version` int DEFAULT 1 COMMENT '版本号',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_title` (`title`),
  KEY `idx_confidence` (`confidence_score`)
) ENGINE=InnoDB AUTO_INCREMENT=2673 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品实体表 - v3.0知识图谱增强版';

-- ----------------------------
-- Table structure for entity_disambiguation
-- ----------------------------
DROP TABLE IF EXISTS `entity_disambiguation`;
CREATE TABLE `entity_disambiguation` (
  `id` int NOT NULL AUTO_INCREMENT,
  `entity_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始实体名称',
  `canonical_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标准化实体名称',
  `similarity_score` decimal(3,2) DEFAULT 0.00 COMMENT '相似度分数 (0.00-1.00)',
  `disambiguation_rule` varchar(500) DEFAULT NULL COMMENT '消歧义规则/策略',
  `entity_type` enum('celebrity', 'work', 'event') DEFAULT 'celebrity' COMMENT '实体类型',
  `context_info` text COMMENT '上下文信息',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_entity_name` (`entity_name`),
  KEY `idx_canonical_name` (`canonical_name`),
  KEY `idx_similarity` (`similarity_score`),
  KEY `idx_type` (`entity_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体消歧义记录表 - v3.0知识图谱功能';

-- ----------------------------
-- Table structure for knowledge_quality
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_quality`;
CREATE TABLE `knowledge_quality` (
  `id` int NOT NULL AUTO_INCREMENT,
  `entity_type` enum('celebrity', 'work', 'event', 'relation') NOT NULL COMMENT '实体/关系类型',
  `entity_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '实体/关系ID',
  `quality_score` decimal(3,2) DEFAULT 0.00 COMMENT '总体质量分数 (0.00-1.00)',
  `completeness` decimal(3,2) DEFAULT 0.00 COMMENT '完整性分数 (0.00-1.00)',
  `consistency` decimal(3,2) DEFAULT 0.00 COMMENT '一致性分数 (0.00-1.00)',
  `accuracy` decimal(3,2) DEFAULT 0.00 COMMENT '准确性分数 (0.00-1.00)',
  `assessment_method` varchar(100) DEFAULT 'auto' COMMENT '评估方法: auto/manual/hybrid',
  `quality_grade` enum('EXCELLENT', 'GOOD', 'FAIR', 'POOR', 'VERY_POOR') DEFAULT 'FAIR' COMMENT '质量等级',
  `issues_found` text COMMENT '发现的问题描述',
  `improvement_suggestions` text COMMENT '改进建议',
  `last_assessed` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '最后评估时间',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_entity_type_id` (`entity_type`, `entity_id`),
  KEY `idx_quality_score` (`quality_score`),
  KEY `idx_quality_grade` (`quality_grade`),
  KEY `idx_last_assessed` (`last_assessed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识质量评估表 - v3.0知识图谱功能';

-- ----------------------------
-- Table structure for kg_statistics
-- ----------------------------
DROP TABLE IF EXISTS `kg_statistics`;
CREATE TABLE `kg_statistics` (
  `id` int NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL COMMENT '统计日期',
  `total_entities` int DEFAULT 0 COMMENT '总实体数',
  `total_relations` int DEFAULT 0 COMMENT '总关系数',
  `avg_quality_score` decimal(3,2) DEFAULT 0.00 COMMENT '平均质量分数',
  `disambiguation_rate` decimal(3,2) DEFAULT 0.00 COMMENT '消歧义率',
  `celebrity_count` int DEFAULT 0 COMMENT '人员实体数',
  `work_count` int DEFAULT 0 COMMENT '作品实体数',
  `event_count` int DEFAULT 0 COMMENT '事件实体数',
  `high_quality_entities` int DEFAULT 0 COMMENT '高质量实体数(quality_score >= 0.8)',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`),
  KEY `idx_total_entities` (`total_entities`),
  KEY `idx_avg_quality` (`avg_quality_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识图谱统计信息表 - v3.0知识图谱功能';

-- ----------------------------
-- Insert initial statistics data
-- ----------------------------
INSERT INTO `kg_statistics` (`stat_date`, `total_entities`, `total_relations`, `avg_quality_score`, `disambiguation_rate`) 
VALUES (CURDATE(), 0, 0, 0.00, 0.00);

SET FOREIGN_KEY_CHECKS = 1;
