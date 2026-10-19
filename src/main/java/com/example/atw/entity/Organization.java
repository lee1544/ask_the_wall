package com.example.atw.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_organization")
public class Organization implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String orgCode;
    
    @Column(nullable = false, length = 100)
    private String orgName;

    private String description;

    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}
