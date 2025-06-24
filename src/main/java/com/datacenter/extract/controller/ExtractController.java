package com.datacenter.extract.controller;

import com.alibaba.fastjson2.JSONObject;
import com.datacenter.extract.service.TextExtractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ExtractController {

    private final TextExtractionService textExtractionService;

    @Autowired
    public ExtractController(TextExtractionService textExtractionService) {
        this.textExtractionService = textExtractionService;
    }

    @PostMapping("/extract")
    public CompletableFuture<JSONObject> extract(@RequestBody JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            String text = request.getString("text");
            String extractType = request.getString("extractType");
            if (extractType == null || extractType.isEmpty()) {
                extractType = "triples";
            }

            if (text == null || text.trim().isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "文本内容不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            // 正确的调用关系：Controller → TextExtractionService
            String result = textExtractionService.extractTriples(text, extractType, null);
            return JSONObject.parseObject(result);
        });
    }

    @PostMapping("/extract/batch")
    public CompletableFuture<JSONObject> batchExtract(@RequestBody JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> texts = request.getList("texts", String.class);
            String extractType = request.getString("extractType");
            if (extractType == null || extractType.isEmpty()) {
                extractType = "triples";
            }

            if (texts == null || texts.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "文本列表不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            String textsJson = "[" + texts.stream()
                    .map(t -> "\"" + t.replace("\"", "\\\"") + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse("") + "]";

            // 正确的调用关系：Controller → TextExtractionService
            String result = textExtractionService.batchExtract(textsJson, extractType, null);
            return JSONObject.parseObject(result);
        });
    }

    @PostMapping("/extract/social")
    public CompletableFuture<JSONObject> extractSocial(@RequestBody JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            String text = request.getString("text");
            String extractTypes = request.getString("extractTypes");
            Boolean maskSensitive = request.getBoolean("maskSensitive");

            if (text == null || text.trim().isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "文本内容不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            // 正确的调用关系：Controller → TextExtractionService
            String result = textExtractionService.extractSocialInfo(
                text,
                extractTypes != null ? extractTypes : "entities,relations",
                maskSensitive != null ? maskSensitive : false
            );
            return JSONObject.parseObject(result);
        });
    }

    @GetMapping("/health")
    public JSONObject health() {
        // 正确的调用关系：Controller → TextExtractionService
        String healthResult = textExtractionService.healthCheck();
        return JSONObject.parseObject(healthResult);
    }

    @GetMapping("/info")
    public JSONObject info() {
        JSONObject info = new JSONObject();
        info.put("service", "intelligent-extraction-service");
        info.put("version", "1.0.0");
        info.put("description", "按照正确分层架构设计的智能文本提取服务");
        info.put("architecture", "ExtractController → TextExtractionService → SmartAIProvider → AIModelCaller");
        info.put("principle", "Controller不直接调用大模型，严格遵循分层架构");
        info.put("call_relationship", "✅ 正确：Controller只调用TextExtractionService");
        info.put("data_flow", "文本输入 → AI提取 → 数据库保存 → 结果返回");
        info.put("timestamp", System.currentTimeMillis());
        return info;
    }
}
