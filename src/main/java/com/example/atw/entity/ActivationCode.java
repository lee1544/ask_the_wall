package com.example.atw.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 激活码：厂商在服务端批量生成，客户在客户端输入后触发签发，一码一机。
 */
@Data
@Entity
@Table(name = "t_activation_code")
public class ActivationCode implements Serializable {

    /** 未使用 */
    public static final int STATUS_UNUSED = 0;

    /** 已激活（已绑定机器并签发 License） */
    public static final int STATUS_ACTIVATED = 1;

    /** 已禁用 */
    public static final int STATUS_DISABLED = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private Integer status;

    /** 批次标识，便于按批次管理 */
    @Column(length = 64)
    private String batch;

    /** 被授权方名称 */
    @Column(length = 100)
    private String licensee;

    /** 版本 / 授权类型 */
    @Column(length = 50)
    private String edition;

    /** License 有效天数，null 或 <=0 表示永久 */
    private Integer validDays;

    /** 激活码自身的使用截止时间，null 表示不限 */
    private LocalDateTime deadlineAt;

    /** 绑定的机器码（归一化后） */
    @Column(length = 100)
    private String machineCode;

    /** 签发的 License 编号 */
    @Column(length = 64)
    private String licenseId;

    /** 签发的 License 授权码原文 */
    @Column(columnDefinition = "TEXT")
    private String licenseKey;

    private LocalDateTime activatedAt;

    @Column(length = 255)
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
