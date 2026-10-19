package com.example.atw.constant;

/**
 * @author Li Jian on 2026/6/21.
 */
public class SystemConstant {

    public static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    public static final String ORG_ADMIN_ROLE_CODE_PREFIX = "ORG_ADMIN_";

    public static final String NORMAL_ADMIN_ROLE_CODE_PREFIX = "NORMAL_ADMIN_";

    public static final String DEFAULT_SYSTEM_PASSWORD = "12345678";

    public static final String[] ORG_ADMIN_PERMISSIONS = new String[]{
            "user:view", "user:create", "user:update", "user:delete", "user:assign-role", "user:reset-password",
            "org:view", "org:update",
            "role:view", "role:create", "role:update", "role:delete", "role:assign-permission"
    };

    public static final String[] NORMAL_ADMIN_PERMISSIONS = new String[]{
            "user:view", "user:update"
    };
}
