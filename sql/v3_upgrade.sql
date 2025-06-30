-- ========================================
-- v3.0 知识图谱升级SQL脚本
-- 在现有表基础上添加新字段和新表
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ========================================
-- 步骤1: 为现有表添加v3.0新字段
-- ========================================

-- 为celebrity表添加v3.0字段
ALTER TABLE `celebrity` 
ADD COLUMN `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
ADD COLUMN `version` int DEFAULT 1 COMMENT '版本号',
ADD COLUMN `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
ADD COLUMN `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
ADD INDEX `idx_name` (`name`),
ADD INDEX `idx_confidence` (`confidence_score`);

-- 为work表添加v3.0字段
ALTER TABLE `work` 
ADD COLUMN `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
ADD COLUMN `version` int DEFAULT 1 COMMENT '版本号',
ADD COLUMN `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
ADD COLUMN `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
ADD INDEX `idx_title` (`title`),
ADD INDEX `idx_confidence` (`confidence_score`);

-- 为event表添加v3.0字段
ALTER TABLE `event` 
ADD COLUMN `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
ADD COLUMN `version` int DEFAULT 1 COMMENT '版本号',
ADD COLUMN `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
ADD COLUMN `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
ADD INDEX `idx_event_name` (`event_name`),
ADD INDEX `idx_confidence` (`confidence_score`);

-- 为celebrity_celebrity表添加v3.0字段
ALTER TABLE `celebrity_celebrity` 
CHANGE COLUMN `from` `from_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源实体ID',
CHANGE COLUMN `to` `to_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标实体ID',
ADD COLUMN `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
ADD COLUMN `source_info` varchar(500) DEFAULT NULL COMMENT '来源信息',
ADD COLUMN `version` int DEFAULT 1 COMMENT '版本号',
ADD COLUMN `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
ADD COLUMN `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
ADD INDEX `idx_from_to` (`from_id`, `to_id`),
ADD INDEX `idx_confidence` (`confidence_score`);

-- 为celebrity_work表添加v3.0字段
ALTER TABLE `celebrity_work` 
CHANGE COLUMN `from` `from_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源实体ID',
CHANGE COLUMN `to` `to_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标实体ID',
ADD COLUMN `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
ADD COLUMN `source_info` varchar(500) DEFAULT NULL COMMENT '来源信息',
ADD COLUMN `version` int DEFAULT 1 COMMENT '版本号',
ADD COLUMN `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
ADD COLUMN `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
ADD INDEX `idx_from_to` (`from_id`, `to_id`),
ADD INDEX `idx_confidence` (`confidence_score`);

-- 为celebrity_event表添加v3.0字段
ALTER TABLE `celebrity_event` 
CHANGE COLUMN `from` `from_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源实体ID',
CHANGE COLUMN `to` `to_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标实体ID',
ADD COLUMN `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
ADD COLUMN `source_info` varchar(500) DEFAULT NULL COMMENT '来源信息',
ADD COLUMN `version` int DEFAULT 1 COMMENT '版本号',
ADD COLUMN `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
ADD COLUMN `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
ADD INDEX `idx_from_to` (`from_id`, `to_id`),
ADD INDEX `idx_confidence` (`confidence_score`);

-- 为event_work表添加v3.0字段
ALTER TABLE `event_work` 
CHANGE COLUMN `from` `from_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源实体ID',
CHANGE COLUMN `to` `to_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标实体ID',
ADD COLUMN `confidence_score` decimal(3,2) DEFAULT 0.80 COMMENT '置信度分数 (0.00-1.00)',
ADD COLUMN `source_info` varchar(500) DEFAULT NULL COMMENT '来源信息',
ADD COLUMN `version` int DEFAULT 1 COMMENT '版本号',
ADD COLUMN `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
ADD COLUMN `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
ADD INDEX `idx_from_to` (`from_id`, `to_id`),
ADD INDEX `idx_confidence` (`confidence_score`);

-- ========================================
-- 步骤2: 创建v3.0新增表
-- ========================================

-- 创建实体消歧义记录表
CREATE TABLE IF NOT EXISTS `entity_disambiguation` (
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

-- 创建知识质量评估表
CREATE TABLE IF NOT EXISTS `knowledge_quality` (
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

-- 创建知识图谱统计信息表
CREATE TABLE IF NOT EXISTS `kg_statistics` (
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

-- ========================================
-- 步骤3: 插入初始数据
-- ========================================

-- 插入初始统计数据（如果不存在）
INSERT IGNORE INTO `kg_statistics` (`stat_date`, `total_entities`, `total_relations`, `avg_quality_score`, `disambiguation_rate`) 
VALUES (CURDATE(), 0, 0, 0.00, 0.00);

-- ========================================
-- 步骤4: 验证升级结果
-- ========================================

-- 显示所有表
SELECT 'Tables created successfully:' as 'V3.0 Upgrade Status';
SHOW TABLES;

-- 显示新增表的结构
SELECT 'Entity Disambiguation Table:' as 'New Table';
DESCRIBE entity_disambiguation;

SELECT 'Knowledge Quality Table:' as 'New Table';
DESCRIBE knowledge_quality;

SELECT 'KG Statistics Table:' as 'New Table';
DESCRIBE kg_statistics;

SET FOREIGN_KEY_CHECKS = 1;

SELECT '✅ v3.0 Knowledge Graph Database Upgrade Completed Successfully!' as 'Status'; 