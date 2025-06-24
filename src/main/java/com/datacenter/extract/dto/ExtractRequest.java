package com.datacenter.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文本提取请求DTO
 * 标准JSON格式的请求参数
 */
public class ExtractRequest {

    private String text;

    @JsonProperty("extractTypes")
    private String extractTypes = "entities,relations";

    @JsonProperty("maskSensitive")
    private Boolean maskSensitive = false;

    // 默认构造函数
    public ExtractRequest() {
    }

    // 带参构造函数
    public ExtractRequest(String text, String extractTypes, Boolean maskSensitive) {
        this.text = text;
        this.extractTypes = extractTypes;
        this.maskSensitive = maskSensitive;
    }

    // Getter和Setter方法
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getExtractTypes() {
        return extractTypes;
    }

    public void setExtractTypes(String extractTypes) {
        this.extractTypes = extractTypes;
    }

    public Boolean getMaskSensitive() {
        return maskSensitive;
    }

    public void setMaskSensitive(Boolean maskSensitive) {
        this.maskSensitive = maskSensitive;
    }

    @Override
    public String toString() {
        return "ExtractRequest{" +
                "text='" + text + '\'' +
                ", extractTypes='" + extractTypes + '\'' +
                ", maskSensitive=" + maskSensitive +
                '}';
    }
}