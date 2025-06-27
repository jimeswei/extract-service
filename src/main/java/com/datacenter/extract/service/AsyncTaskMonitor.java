package com.datacenter.extract.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 异步任务监控服务
 * 
 * 功能：
 * 1. 任务状态追踪
 * 2. 性能指标统计
 * 3. 错误监控
 * 4. 资源使用监控
 */
@Service
public class AsyncTaskMonitor {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskMonitor.class);

    // 任务执行统计
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong successTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    // 活跃任务追踪
    private final Map<String, TaskInfo> activeTasks = new ConcurrentHashMap<>();

    /**
     * 记录任务开始
     */
    public String recordTaskStart(String textInput, String extractParams) {
        String taskId = generateTaskId();
        TaskInfo taskInfo = new TaskInfo(taskId, textInput, extractParams, System.currentTimeMillis());
        activeTasks.put(taskId, taskInfo);
        totalTasks.incrementAndGet();

        log.debug("任务开始 - TaskId: {}, TextLength: {}, ExtractParams: {}",
                taskId, textInput != null ? textInput.length() : 0, extractParams);
        return taskId;
    }

    /**
     * 记录任务成功
     */
    public void recordTaskSuccess(String taskId) {
        TaskInfo taskInfo = activeTasks.remove(taskId);
        if (taskInfo != null) {
            long duration = System.currentTimeMillis() - taskInfo.startTime;
            totalProcessingTime.addAndGet(duration);
            successTasks.incrementAndGet();

            log.info("任务成功 - TaskId: {}, Duration: {}ms", taskId, duration);
        }
    }

    /**
     * 记录任务失败
     */
    public void recordTaskFailed(String taskId, String errorMessage) {
        TaskInfo taskInfo = activeTasks.remove(taskId);
        if (taskInfo != null) {
            long duration = System.currentTimeMillis() - taskInfo.startTime;
            failedTasks.incrementAndGet();

            log.error("任务失败 - TaskId: {}, Duration: {}ms, Error: {}",
                    taskId, duration, errorMessage);
        }
    }

    /**
     * 获取监控统计
     */
    public Map<String, Object> getMonitorStats() {
        Map<String, Object> stats = new HashMap<>();

        long total = totalTasks.get();
        long success = successTasks.get();
        long failed = failedTasks.get();

        stats.put("total_tasks", total);
        stats.put("success_tasks", success);
        stats.put("failed_tasks", failed);
        stats.put("active_tasks", activeTasks.size());
        stats.put("success_rate", total > 0 ? (double) success / total * 100 : 0.0);
        stats.put("failure_rate", total > 0 ? (double) failed / total * 100 : 0.0);

        if (success > 0) {
            stats.put("avg_processing_time", totalProcessingTime.get() / success);
        } else {
            stats.put("avg_processing_time", 0);
        }

        return stats;
    }

    /**
     * 获取活跃任务信息
     */
    public Map<String, TaskInfo> getActiveTasks() {
        return new HashMap<>(activeTasks);
    }

    /**
     * 清理超时任务
     */
    public void cleanupTimeoutTasks() {
        long currentTime = System.currentTimeMillis();
        long timeoutThreshold = 5 * 60 * 1000; // 5分钟超时

        activeTasks.entrySet().removeIf(entry -> {
            TaskInfo taskInfo = entry.getValue();
            if (currentTime - taskInfo.startTime > timeoutThreshold) {
                log.warn("清理超时任务 - TaskId: {}, Duration: {}ms",
                        entry.getKey(), currentTime - taskInfo.startTime);
                failedTasks.incrementAndGet();
                return true;
            }
            return false;
        });
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 任务信息内部类
     */
    public static class TaskInfo {
        public final String taskId;
        public final String textInput;
        public final String extractParams;
        public final long startTime;

        public TaskInfo(String taskId, String textInput, String extractParams, long startTime) {
            this.taskId = taskId;
            this.textInput = textInput;
            this.extractParams = extractParams;
            this.startTime = startTime;
        }

        public long getDuration() {
            return System.currentTimeMillis() - startTime;
        }
    }
}