package com.example.atw.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CreateOrganizationRequest {

    @NotBlank(message = "组织编码不能为空")
    private String orgCode;

    @NotBlank(message = "组织名称不能为空")
    private String orgName;

    private String description;

    @NotBlank(message = "管理员用户名不能为空")
    private String adminUsername;

    @NotBlank(message = "管理员密码不能为空")
    private String adminPassword;

    @NotBlank(message = "管理员姓名不能为空")
    private String adminRealName;
}
