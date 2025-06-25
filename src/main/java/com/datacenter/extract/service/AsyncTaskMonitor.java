package com.datacenter.extract.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * 异步任务监控服务
 * 用于监控和统计异步文本提取任务的执行情况
 */
@Service
public class AsyncTaskMonitor {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskMonitor.class);

    // 任务统计计数器
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong completedTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    private final AtomicLong runningTasks = new AtomicLong(0);

    // 任务执行时间统计
    private final ConcurrentHashMap<String, Long> taskStartTimes = new ConcurrentHashMap<>();
    private final AtomicLong totalExecutionTime = new AtomicLong(0);

    /**
     * 记录任务开始
     */
    public String recordTaskStart(String textInput, String extractParams) {
        String taskId = generateTaskId();
        long startTime = System.currentTimeMillis();

        taskStartTimes.put(taskId, startTime);
        totalTasks.incrementAndGet();
        runningTasks.incrementAndGet();

        log.info("异步任务开始 - 任务ID: {}, 输入长度: {}, 参数: {}",
                taskId, textInput != null ? textInput.length() : 0, extractParams);

        return taskId;
    }

    /**
     * 记录任务完成
     */
    public void recordTaskComplete(String taskId) {
        Long startTime = taskStartTimes.remove(taskId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            totalExecutionTime.addAndGet(duration);
            completedTasks.incrementAndGet();
            runningTasks.decrementAndGet();

            log.info("异步任务完成 - 任务ID: {}, 执行时间: {}ms", taskId, duration);
        }
    }

    /**
     * 记录任务失败
     */
    public void recordTaskFailed(String taskId, String errorMessage) {
        Long startTime = taskStartTimes.remove(taskId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            failedTasks.incrementAndGet();
            runningTasks.decrementAndGet();

            log.error("异步任务失败 - 任务ID: {}, 执行时间: {}ms, 错误: {}",
                    taskId, duration, errorMessage);
        }
    }

    /**
     * 获取监控统计信息
     */
    public Map<String, Object> getMonitorStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        long total = totalTasks.get();
        long completed = completedTasks.get();
        long failed = failedTasks.get();
        long running = runningTasks.get();
        long totalTime = totalExecutionTime.get();

        // 基础统计
        stats.put("total_tasks", total);
        stats.put("completed_tasks", completed);
        stats.put("failed_tasks", failed);
        stats.put("running_tasks", running);

        // 成功率统计
        if (total > 0) {
            stats.put("success_rate", String.format("%.2f%%", (double) completed / total * 100));
            stats.put("failure_rate", String.format("%.2f%%", (double) failed / total * 100));
        } else {
            stats.put("success_rate", "0.00%");
            stats.put("failure_rate", "0.00%");
        }

        // 性能统计
        if (completed > 0) {
            stats.put("average_execution_time", String.format("%.2fms", (double) totalTime / completed));
        } else {
            stats.put("average_execution_time", "0.00ms");
        }

        stats.put("total_execution_time", totalTime + "ms");

        // 系统状态
        stats.put("status", running > 0 ? "processing" : "idle");
        stats.put("timestamp", System.currentTimeMillis());

        return stats;
    }

    /**
     * 重置监控统计
     */
    public void resetStats() {
        totalTasks.set(0);
        completedTasks.set(0);
        failedTasks.set(0);
        runningTasks.set(0);
        totalExecutionTime.set(0);
        taskStartTimes.clear();

        log.info("异步任务监控统计已重置");
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" +
                Integer.toHexString((int) (Math.random() * 0x10000));
    }

    /**
     * 获取当前运行任务数
     */
    public long getRunningTaskCount() {
        return runningTasks.get();
    }

    /**
     * 检查是否有任务正在运行
     */
    public boolean hasRunningTasks() {
        return runningTasks.get() > 0;
    }
}