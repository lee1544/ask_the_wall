package com.example.atw.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 批量生成激活码请求（厂商侧管理接口）。
 */
@Data
public class GenerateCodesRequest {

    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量至少为 1")
    @Max(value = 500, message = "单次最多生成 500 个激活码")
    private Integer count;

    /** 批次标识 */
    private String batch;

    /** 被授权方名称 */
    private String licensee;

    /** 版本 / 授权类型，默认 standard */
    private String edition;

    /** License 有效天数，null 或 <=0 表示永久 */
    private Integer validDays;

    /** 激活码自身的使用截止天数（自生成之日起），null 或 <=0 表示不限 */
    private Integer deadlineDays;

    private String remark;
}
