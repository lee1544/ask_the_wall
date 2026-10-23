package com.example.atw.client.dto;

import com.example.atw.client.config.WebSearchMode;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class ChatModelConfigRequest {

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称不能超过100个字符")
    private String configName;

    @NotBlank(message = "接口地址不能为空")
    @Size(max = 255, message = "接口地址不能超过255个字符")
    private String baseUrl;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 120, message = "模型名称不能超过120个字符")
    private String modelName;

    @NotBlank(message = "API Key 环境变量不能为空")
    @Size(max = 100, message = "API Key 环境变量不能超过100个字符")
    @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]*", message = "API Key 环境变量格式不正确")
    private String apiKeyEnv;

    @NotBlank(message = "对话路径不能为空")
    @Size(max = 120, message = "对话路径不能超过120个字符")
    @Pattern(regexp = "/.*", message = "对话路径必须以 / 开头")
    private String chatPath;

    @NotNull(message = "上下文预算不能为空")
    @Min(value = 256, message = "上下文预算不能小于256")
    @Max(value = 1048576, message = "上下文预算不能大于1048576")
    private Integer contextBudget;

    @NotNull(message = "最大输出 Token 不能为空")
    @Min(value = 1, message = "最大输出 Token 不能小于1")
    @Max(value = 262144, message = "最大输出 Token 不能大于262144")
    private Integer maxOutputTokens;

    @NotNull(message = "连接超时不能为空")
    @Min(value = 1000, message = "连接超时不能小于1000毫秒")
    @Max(value = 600000, message = "连接超时不能大于600000毫秒")
    private Integer connectTimeoutMs;

    @NotNull(message = "读取超时不能为空")
    @Min(value = 1000, message = "读取超时不能小于1000毫秒")
    @Max(value = 600000, message = "读取超时不能大于600000毫秒")
    private Integer readTimeoutMs;

    private Boolean webSearchEnabled;

    private WebSearchMode webSearchMode;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
