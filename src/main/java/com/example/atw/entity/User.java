package com.example.atw.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_user")
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @JsonIgnore
    @Column(nullable = false)
    private String password;
    
    @Column(length = 50)
    private String realName;
    
    @Column(nullable = false)
    private Integer orgId;
    
    private Integer status;
    
    private LocalDateTime lastLoginTime;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    @Transient
    private String orgName;
}
