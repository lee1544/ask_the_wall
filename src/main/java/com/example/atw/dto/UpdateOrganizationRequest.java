package com.example.atw.dto;

import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class UpdateOrganizationRequest {

    @Size(max = 50, message = "组织编码长度不能超过50个字符")
    private String orgCode;

    @Size(max = 100, message = "组织名称长度不能超过100个字符")
    private String orgName;

    @Size(max = 500, message = "描述长度不能超过500个字符")
    private String description;

    private Integer status;
}