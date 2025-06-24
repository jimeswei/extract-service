package com.datacenter.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 社交关系提取请求DTO
 * 专用于社交关系接口的请求参数
 */
public class SocialExtractRequest {

    private String text;

    private SocialOptions options;

    // 默认构造函数
    public SocialExtractRequest() {
    }

    // 带参构造函数
    public SocialExtractRequest(String text, SocialOptions options) {
        this.text = text;
        this.options = options;
    }

    // Getter和Setter方法
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public SocialOptions getOptions() {
        return options;
    }

    public void setOptions(SocialOptions options) {
        this.options = options;
    }

    /**
     * 社交关系提取选项
     */
    public static class SocialOptions {

        @JsonProperty("mask_sensitive")
        private Boolean maskSensitive = false;

        public SocialOptions() {
        }

        public SocialOptions(Boolean maskSensitive) {
            this.maskSensitive = maskSensitive;
        }

        public Boolean getMaskSensitive() {
            return maskSensitive;
        }

        public void setMaskSensitive(Boolean maskSensitive) {
            this.maskSensitive = maskSensitive;
        }
    }

    @Override
    public String toString() {
        return "SocialExtractRequest{" +
                "text='" + text + '\'' +
                ", options=" + options +
                '}';
    }
}