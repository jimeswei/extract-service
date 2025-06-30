package com.datacenter.extract.util;

import com.alibaba.fastjson2.JSONObject;

/**
 * 统一响应构建器 - 企业级标准化响应格式
 * 
 * 🏗️ 设计模式：建造者模式 (Builder Pattern)
 * 🎯 设计目标：
 * 1. 统一API响应格式，提升用户体验
 * 2. 链式调用，代码优雅简洁
 * 3. 类型安全，避免响应格式错误
 * 4. 可扩展性，便于后续功能扩展
 * 
 * 🚀 响应格式：
 * {
 * "success": true/false,
 * "status": "success/error/warning",
 * "message": "响应描述",
 * "data": {...}, // 业务数据
 * "timestamp": 1234567890,
 * "response_time": "50ms"
 * }
 */
public class ResponseBuilder {

    private final JSONObject response;

    /**
     * 私有构造函数，强制使用静态工厂方法
     */
    private ResponseBuilder(boolean success, ResponseStatus status) {
        this.response = new JSONObject();
        this.response.put("success", success);
        this.response.put("status", status.getValue());
        this.response.put("timestamp", System.currentTimeMillis());
    }

    /**
     * 创建成功响应
     */
    public static ResponseBuilder success() {
        return new ResponseBuilder(true, ResponseStatus.SUCCESS);
    }

    /**
     * 创建成功响应并设置消息
     */
    public static ResponseBuilder success(String message) {
        return new ResponseBuilder(true, ResponseStatus.SUCCESS).message(message);
    }

    /**
     * 创建错误响应
     */
    public static ResponseBuilder error(String message) {
        return new ResponseBuilder(false, ResponseStatus.ERROR).message(message);
    }

    /**
     * 创建警告响应
     */
    public static ResponseBuilder warning(String message) {
        return new ResponseBuilder(true, ResponseStatus.WARNING).message(message);
    }

    /**
     * 设置响应消息
     */
    public ResponseBuilder message(String message) {
        if (message != null && !message.trim().isEmpty()) {
            this.response.put("message", message.trim());
        }
        return this;
    }

    /**
     * 添加业务数据
     */
    public ResponseBuilder data(String key, Object value) {
        if (key != null && !key.trim().isEmpty()) {
            this.response.put(key, value);
        }
        return this;
    }

    /**
     * 批量添加业务数据
     */
    public ResponseBuilder data(JSONObject data) {
        if (data != null && !data.isEmpty()) {
            this.response.putAll(data);
        }
        return this;
    }

    /**
     * 设置响应时间
     */
    public ResponseBuilder responseTime(long responseTimeMs) {
        this.response.put("response_time", responseTimeMs + "ms");
        return this;
    }

    /**
     * 设置响应时间（带单位）
     */
    public ResponseBuilder responseTime(String responseTime) {
        if (responseTime != null && !responseTime.trim().isEmpty()) {
            this.response.put("response_time", responseTime.trim());
        }
        return this;
    }

    /**
     * 设置请求ID（用于链路追踪）
     */
    public ResponseBuilder requestId(String requestId) {
        if (requestId != null && !requestId.trim().isEmpty()) {
            this.response.put("request_id", requestId.trim());
        }
        return this;
    }

    /**
     * 设置任务ID（用于异步任务追踪）
     */
    public ResponseBuilder taskId(String taskId) {
        if (taskId != null && !taskId.trim().isEmpty()) {
            this.response.put("task_id", taskId.trim());
        }
        return this;
    }

    /**
     * 添加分页信息
     */
    public ResponseBuilder pagination(int page, int size, long total) {
        JSONObject pagination = new JSONObject();
        pagination.put("page", page);
        pagination.put("size", size);
        pagination.put("total", total);
        pagination.put("pages", (total + size - 1) / size);
        this.response.put("pagination", pagination);
        return this;
    }

    /**
     * 添加性能指标
     */
    public ResponseBuilder metrics(String key, Object value) {
        JSONObject metrics = this.response.getJSONObject("metrics");
        if (metrics == null) {
            metrics = new JSONObject();
            this.response.put("metrics", metrics);
        }
        metrics.put(key, value);
        return this;
    }

    /**
     * 构建最终响应
     */
    public JSONObject build() {
        return this.response;
    }

    /**
     * 响应状态枚举
     */
    public enum ResponseStatus {
        SUCCESS("success"),
        ERROR("error"),
        WARNING("warning"),
        INFO("info");

        private final String value;

        ResponseStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    // ========================= 便捷静态方法 =========================

    /**
     * 快速创建成功响应
     */
    public static JSONObject ok() {
        return success("操作成功").build();
    }

    /**
     * 快速创建成功响应并附带数据
     */
    public static JSONObject ok(String key, Object value) {
        return success("操作成功").data(key, value).build();
    }

    /**
     * 快速创建错误响应
     */
    public static JSONObject fail(String message) {
        return error(message).build();
    }

    /**
     * 快速创建参数错误响应
     */
    public static JSONObject badRequest(String message) {
        return error("请求参数错误: " + message).build();
    }

    /**
     * 快速创建系统错误响应
     */
    public static JSONObject systemError() {
        return error("系统繁忙，请稍后重试").build();
    }

    /**
     * 快速创建未授权响应
     */
    public static JSONObject unauthorized() {
        return error("未授权访问").build();
    }

    /**
     * 快速创建资源不存在响应
     */
    public static JSONObject notFound(String resource) {
        return error("资源不存在: " + resource).build();
    }
}