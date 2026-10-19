package com.example.atw.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserInfo {
    
    private Integer id;
    
    private String username;
    
    private String realName;
    
    private Integer orgId;
    
    private String orgName;
    
    private List<String> roles;
    
    private List<String> permissions;

    private boolean firstLogin;
}
