# Ask The Wall (用户权限模块)

基于 `content_review_platform` 搭建的 Spring Boot 脚手架，仅保留**用户权限模块**：登录认证、组织管理、用户管理、角色管理、权限分配，并新增**License 授权（服务端签发 + 客户端机器码绑定）**。

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 1.8 |
| Spring Boot | 2.6.13 |
| Spring Security + JWT (jjwt) | 0.9.1 |
| Spring Data JPA | 2.6.13 |
| Druid / MySQL | 1.2.20 / 8.0.33 |
| Lombok | - |

## 快速开始

1. 初始化数据库（建表 + 初始数据）：

   ```bash
   mysql -u root -p ask_the_wall < src/main/resources/static/system_init.sql
   ```

   默认超级管理员：**admin / admin123**

2. 配置数据库连接（可选，默认见 `application-dev.properties`）：

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   或使用环境变量：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`。

3. 单元测试（需要 Maven 3.5+，JUnit 5）：

   ```bash
   mvn test
   ```

## 目录结构

```
src/main/java/com/example/atw
├── AskTheWallApplication.java
├── annotation/     RequirePermission 权限注解
├── config/         SecurityConfig、CorsConfig
├── constant/       SystemConstant（角色/权限常量）
├── dto/            请求响应对象
├── entity/         User、Organization、Role、Permission、UserRole、RolePermission、ActivationCode
├── exception/      统一异常与响应
├── filter/         JWT 认证过滤器、权限切面、Token 黑名单、权限缓存、License 二次校验过滤器
├── repository/     JPA 仓储
├── service/        Auth / User / Organization / Role / Permission / UserRole / License / LicenseKey / ActivationCode
├── utils/          JwtTokenUtil、UserContextUtil、LicenseCryptoUtil、MachineCodeUtil
└── web/            Auth / User / Organization / Role / License 控制器
```

## License 授权（服务端签发 + 客户端机器码绑定）

### 一分钟理解

- **激活码** = 厂商发给客户的「兑换券」，服务端批量生成，一码一机。
- **License** = 客户用兑换券换出的「身份证」，**绑定客户电脑的机器码**，由服务端**私钥**签名防伪。
- **每次业务请求**，客户端把 License 放进 `X-License` 头，服务端校验真伪与是否仍有效。
- 私钥只在服务端；客户端只内置公钥，可离线验签。

### 架构

- **服务端（阿里云，本 Spring Boot 应用）**：厂商授权的**签发端**，持有 RSA **私钥**，负责管理激活码、签发 License，并对业务接口做**二次校验**。
- **客户端（Electron 打包的桌面程序，装在客户机）**：采集本机**机器码**、用内置**公钥**离线验签、解锁 UI，并在业务请求中携带 `X-License`。
- **一码一机**：激活码在服务端生成，首次激活时绑定提交的机器码，之后不可换机。

```
客户机 (Electron 桌面程序)                   阿里云 (本服务 = 厂商签发端)
├─ 采集本机机器码(CPU ProcessorId + 主板 SN)
├─ 用户输入激活码
├─ POST /api/license/activate ──────────────> 校验激活码(存在/未禁用/未过期/未绑定他机)
│   { activationCode, machineCode }            用私钥签发绑定该机器码的 License，落库
│   <──────────────────────────────────────── 返回 licenseKey
├─ 内置公钥验签 + 机器码比对 + 有效期 → 解锁
├─ 本地保存 licenseKey
└─ 业务请求携带 X-License: <licenseKey> ─────> LicenseFilter 校验(签名/有效期/激活状态,支持吊销)
```

### 激活流程

1. 厂商超级管理员批量生成激活码：`POST /api/license/codes`（或先在管理端生成一批发给客户）。
2. 客户端安装后采集机器码（见下方 Electron 代码）。
3. 用户输入激活码，客户端调用 `POST /api/license/activate`。
4. 服务端校验激活码并**用私钥签发**绑定该机器码的 License，保存到 `t_activation_code` 并返回 `licenseKey`。
5. 客户端用**内置公钥**验签 + 机器码比对 + 有效期校验，通过即解锁并本地保存。
6. 之后每次业务请求携带 `X-License`，服务端校验签名、有效期与该 License 是否仍激活（禁用激活码即吊销）。

### 开发联调（后端 + 客户端两人协作）

> 客户端开发**其它功能**期间，后端用 dev profile 启动（`license.enforce=false`），客户端不带 `X-License` 也能调业务接口；**只在联调 License 流程或上线时**才打开校验。

**准备**

1. 建表与权限：新库跑 `src/main/resources/static/system_init.sql`，老库跑 `src/main/resources/static/license_upgrade.sql`（新增 `t_activation_code` 表与 `license:create-code`、`license:view-code` 权限）。
2. 后端启动（首次会自动生成密钥对）：
   ```bash
   java -jar target/ask_the_wall-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
   ```
   生成激活码的账号需有 `license:create-code` 权限（SUPER_ADMIN 默认有）。
3. **把公钥交给客户端**：把 `license/keys/public.key` 的内容粘到客户端代码里（公钥公开，安全）。`private.key` 不要外发。
   - 密钥文件不要删，删除后会重新生成，导致已发 License 全部失效、客户端内置公钥也需同步更换。

**用 Postman / curl 手动跑一遍（不依赖客户端）**

① 登录拿 token
```http
POST http://localhost:8088/api/auth/login
Content-Type: application/json

{"username":"admin","password":"admin123"}
→ {"token":"eyJ...","userInfo":{...}}
```

② 生成激活码
```http
POST http://localhost:8088/api/license/codes
Authorization: Bearer <token>
Content-Type: application/json

{"count":5,"batch":"test","licensee":"测试客户","edition":"standard","validDays":365,"deadlineDays":30}
→ {"count":5,"batch":"test","codes":["ABCD-EFGH-JKLM-NPQR", ...]}
```

③ 激活（`machineCode` 可先用任意 64 位十六进制测试）
```http
POST http://localhost:8088/api/license/activate
Content-Type: application/json

{"activationCode":"ABCD-EFGH-JKLM-NPQR","machineCode":"A1B2C3D4E5F6...（64位hex）"}
→ {"licenseKey":"ATW1.xxx.yyy","licenseId":"...","machineCode":"A1B2-C3D4-...","expiresAt":0}
```

④ 携授权头调业务接口（仅在 `license.enforce=true` 时必需）
```http
GET http://localhost:8088/api/users?page=0&size=10
Authorization: Bearer <token>
X-License: ATW1.xxx.yyy
```

**联调开关**

| 场景 | 设置 |
|------|------|
| 客户端开发其它功能 | dev profile 默认 `license.enforce=true`；调试可设 `LICENSE_ENFORCE=false` 跳过 `X-License` |
| 联调 License 流程 / 上线 | 加 `--license.enforce=true`（或环境变量 `LICENSE_ENFORCE=true`） |

> 客户端与后端不在同一台机器时，地址用后端所在机器的**局域网 IP**（如 `http://192.168.x.x:8088`），并放行 8088（云端注意安全组）。
> 在线自查：`POST /api/license/verify {"licenseKey":"ATW1..."}` → `{valid:true/false, message:...}`。

### 签发格式

`ATW1.<base64url(payload)>.<base64url(signature)>`，签名算法 `SHA256withRSA`（RSA 2048），payload 为 JSON：

| 字段 | 说明 |
|------|------|
| licenseId | License 唯一编号 |
| machineCode | 绑定的机器码（归一化大写，客户端上报值） |
| licensee | 被授权方名称 |
| edition | 版本（默认 standard） |
| issuedAt | 签发时间（epoch 毫秒） |
| expiresAt | 到期时间（epoch 毫秒），`<= 0` 表示永久 |

### 配置项

| 配置 | 说明 | 默认 |
|------|------|------|
| `license.enforce` | 是否开启业务接口的 License 二次校验 | `true`（dev profile 默认 `true`） |
| `license.public-key-path` | 公钥路径（校验用） | `./license/keys/public.key` |
| `license.private-key-path` | 私钥路径（签发用） | `./license/keys/private.key` |

> 首次启动若密钥文件不存在会自动生成 RSA 2048 密钥对。**私钥只能留在服务端**，切勿打进客户端；客户端只内置**公钥**。
> 生成后把 `license/keys/public.key` 的内容复制到 Electron 客户端内置（见下）。

### License 接口

**基础路径：** `/api/license`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/license/activate` | 公开 | 输入激活码激活（服务端签发） |
| POST | `/api/license/verify` | 公开 | 在线校验 License |
| POST | `/api/license/codes` | `license:create-code` | 批量生成激活码 |
| GET | `/api/license/codes` | `license:view-code` | 分页查询激活码 |
| PUT | `/api/license/codes/{id}/status` | `license:create-code` | 启用/禁用（禁用即吊销） |

激活请求 / 响应：

```json
// 请求
{ "activationCode": "ABCD-EFGH-JKLM-NPQR", "machineCode": "A1B2C3D4..." }
// 响应
{ "licenseKey": "ATW1.eyJ...xxx.yyy...", "licenseId": "...", "machineCode": "A1B2-C3D4-...", "expiresAt": 0 }
```

批量生成激活码请求（`validDays` 为 License 有效期，`deadlineDays` 为激活码本身的使用时限；均为空或 `<=0` 表示不限）：

```json
{ "count": 100, "batch": "2026Q1", "licensee": "XX公司", "edition": "standard", "validDays": 365, "deadlineDays": 30 }
```

### Electron 客户端接入

> 机器码采集与本地验签建议放在 **主进程 / preload**，公钥只暴露给校验函数，避免渲染进程直接拿到。

**1. 采集机器码（`main` 进程）**

```js
const { execFileSync } = require('child_process');
const crypto = require('crypto');

const INVALID = new Set([
  '', 'NONE', 'UNKNOWN', '0', 'OEM', 'NOTSPECIFIED', 'NOTAPPLICABLE', 'NOTFOUND',
  'SYSTEMSERIALNUMBER', 'BASESERIALNUMBER', 'TOBEFILLEDBYOEM', 'FILLEDBYOEM',
  'DEFAULTSTRING', 'FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF'
]);

function queryWmi(psExpr) {
  try {
    return execFileSync('powershell',
      ['-NoProfile', '-NonInteractive', '-Command', psExpr],
      { encoding: 'utf8', windowsHide: true }).trim();
  } catch (e) {
    return '';
  }
}

function sanitize(v) {
  const n = (v || '').replace(/[^0-9a-z]/gi, '').toUpperCase();
  return INVALID.has(n) ? '' : n;
}

function getMachineCode() {
  const cpu = sanitize(queryWmi('(Get-CimInstance Win32_Processor).ProcessorId'));
  const board = sanitize(queryWmi('(Get-CimInstance Win32_BaseBoard).SerialNumber'));
  if (!cpu && !board) throw new Error('无法采集机器码');
  return crypto.createHash('sha256')
    .update(`ATW|${cpu}|${board}`, 'utf8')
    .digest('hex').toUpperCase();
}
```

**2. 本地验签（内置公钥）**

```js
const crypto = require('crypto');

// 把服务端 license/keys/public.key 的内容原样粘贴进来
const PUBLIC_KEY_PEM = `-----BEGIN PUBLIC KEY-----
...（服务端生成的公钥）...
-----END PUBLIC KEY-----`;

function verifyLicense(licenseKey, machineCode) {
  const parts = (licenseKey || '').trim().split('.');
  if (parts.length !== 3 || parts[0] !== 'ATW1') return { valid: false, reason: '授权码格式无效' };

  const payloadBuf = Buffer.from(parts[1], 'base64url');
  const signatureBuf = Buffer.from(parts[2], 'base64url');
  if (!crypto.verify('RSA-SHA256', payloadBuf, PUBLIC_KEY_PEM, signatureBuf)) {
    return { valid: false, reason: '授权码签名无效' };
  }

  const payload = JSON.parse(payloadBuf.toString('utf8'));
  if (payload.machineCode !== machineCode) return { valid: false, reason: '授权码与本机不匹配' };
  if (payload.expiresAt > 0 && Date.now() > payload.expiresAt) return { valid: false, reason: '授权已过期' };
  return { valid: true, payload };
}

// 每次启动 / 使用前：
// const { valid } = verifyLicense(storedLicenseKey, getMachineCode());
```

**3. 业务请求携带授权头**

```js
// axios 示例
axios.defaults.headers.common['X-License'] = storedLicenseKey;

// fetch 示例
fetch(url, { headers: { 'X-License': storedLicenseKey, 'Authorization': `Bearer ${token}` } });
```

> **安全提示**：客户端可被反编译/篡改，纯前端校验只能提高破解门槛（校验逻辑放主进程/原生模块、代码混淆）。**真正的防线是服务端的 `X-License` 校验**——签名、有效期由私钥保证，禁用激活码即可远程吊销。

## API 接口

### 通用说明

| 项目 | 说明 |
|------|------|
| 基础路径 | `http://localhost:8088` |
| 认证方式 | `Authorization: Bearer <token>`（JWT） |
| Token 有效期 | 7 天（`jwt.expiration`） |
| 开发模式 | 使用 `DEV-BYPASS-DEBUG-TOKEN` 可绕过登录 |
| 统一错误格式 | `{code, message, data, timestamp}` |

| 状态码 | 说明 |
|-----|------|
| 200 | 成功 |
| 400 | 业务异常 / 参数错误 |
| 401 | 未授权 / 未登录 |
| 403 | 无权限访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 1. 认证 (Auth)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 `{token, userInfo}` |
| GET | `/api/auth/userinfo` | 获取当前用户信息（含角色、权限） |
| POST | `/api/auth/logout` | 退出登录（Token 加入黑名单） |

登录请求体：

```json
{ "username": "admin", "password": "admin123" }
```

### 2. 用户管理 (Users)

基础路径 `/api/users`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/users` | `user:create` | 在当前组织下创建用户 |
| GET | `/api/users?page=0&size=10&realname=` | `user:view` | 分页查询（真名模糊） |
| GET | `/api/users/{id}` | `user:view` | 用户详情 |
| PUT | `/api/users/{id}/status?status=1` | `user:update` | 启用/禁用 |
| PUT | `/api/users/{id}/password` | `user:update` | 修改密码 |
| PUT | `/api/users/{id}/reset-password` | `user:reset-password` | 重置为初始密码（12345678） |
| DELETE | `/api/users/{id}` | `user:delete` | 删除用户 |
| POST | `/api/users/{id}/roles` | `user:assign-role` | 分配角色（角色ID数组） |
| GET | `/api/users/by_role_code?roleCode=` | 登录即可 | 按角色编码查询用户 |

### 3. 组织管理 (Organizations)

基础路径 `/api/organizations`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/organizations` | `org:create` | 创建组织（同时创建初始管理员与默认角色） |
| GET | `/api/organizations/{id}` | `org:view` | 组织详情 |
| GET | `/api/organizations?page=0&size=10` | `org:view` | 分页列表 |
| PUT | `/api/organizations/{id}` | `org:update` | 更新组织 |
| DELETE | `/api/organizations/{id}` | `org:delete` | 删除组织（级联清理用户与角色） |

### 4. 角色管理 (Roles)

基础路径 `/api/roles`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/roles` | `role:view` | 当前组织角色列表 |
| GET | `/api/roles/{id}` | `role:view` | 角色详情 |
| POST | `/api/roles` | `role:create` | 创建角色 |
| PUT | `/api/roles/{id}` | `role:update` | 更新角色 |
| DELETE | `/api/roles/{id}` | `role:delete` | 删除角色 |
| POST | `/api/roles/{id}/permissions` | `role:assign-permission` | 分配权限（权限ID数组） |
| GET | `/api/roles/{id}/permissions` | `role:view` | 查询角色权限 |

## 权限标识对照表

| 权限标识 | 说明 |
|----------|------|
| `user:create` | 创建用户 |
| `user:view` | 查看用户 |
| `user:update` | 更新用户 |
| `user:delete` | 删除用户 |
| `user:assign-role` | 分配角色 |
| `user:reset-password` | 重置密码 |
| `org:create` | 创建组织 |
| `org:view` | 查看组织 |
| `org:update` | 更新组织 |
| `org:delete` | 删除组织 |
| `role:create` | 创建角色 |
| `role:view` | 查看角色 |
| `role:update` | 更新角色 |
| `role:delete` | 删除角色 |
| `role:assign-permission` | 分配权限 |
| `license:create-code` | 生成/管理激活码 |
| `license:view-code` | 查看激活码 |

## 角色模型

| 角色编码 | 说明 |
|----------|------|
| `SUPER_ADMIN` | 全局超级管理员（`org_id` 为 NULL） |
| `ORG_ADMIN_{orgId}` | 组织管理员，创建组织时自动生成 |
| `NORMAL_ADMIN_{orgId}` | 普通管理员，新建用户默认绑定 |

## 客户端模型接口（/api/v1/client）

统一由后端调用 DeepSeek / 百炼，供应商地址、密钥和底层模型名不下发客户端。详细契约见
`src/main/resources/md/backend-api-minimal-contract.md`。

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/client/capabilities` | 能力与版本；登录即可访问，未授权时 `available=false` |
| POST | `/api/v1/client/chat/completions` | 对话（SSE 或 JSON）、摘要 |
| POST | `/api/v1/client/chat/{generationId}/stop` | 停止同一生成组 |
| GET | `/api/v1/client/requests/{requestId}` | 统一恢复查询 |
| POST | `/api/v1/client/voice/recognize` | WAV（PCM16/16kHz/单声道）识别 |
| POST | `/api/v1/client/voice/synthesize` | 按 segmentIndex 合成片段 |
| POST | `/api/v1/client/avatar/generate` | 创建头像异步任务（202） |
| GET | `/api/v1/client/image-jobs/{id}` | 查询头像任务 |
| DELETE | `/api/v1/client/image-jobs/{id}` | 取消头像任务 |
| POST | `/api/v1/client/embeddings` | 1024 维向量 |
| GET | `/api/v1/client/media/{id}` | 临时音频/图片下载（支持 Range） |

鉴权沿用现有 JWT（`Authorization: Bearer`）与 `X-License`。`/capabilities`、
`/requests/**`、`/image-jobs/**`、`/media/**` 和 `/chat/{id}/stop` 不因 License 过期被
LicenseFilter 拦截；生成类接口仍要求有效 License。所有客户端 JSON 错误体为
`{"error","code","requestId"}`，成功 JSON 带 `Cache-Control: no-store`。

### 配置

```properties
DEEPSEEK_API_KEY=...
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-flash
BAILIAN_API_KEY=...
BAILIAN_ORIGIN=https://dashscope.aliyuncs.com
CLIENT_MEDIA_DIR=./data/media
```

其余限制/版本见 `application.properties` 的 `client.*` 段。管理端如调整模型、音色或
分段规则，必须同步更新 `client.chat.space-id`、`client.tts.config-version`、
`client.image.config-version`、`client.embedding.space-id`，否则客户端会收到
`CONFIG_CHANGED` / `INDEX_SPACE_CHANGED`。

### 数据库

新表使用手写 SQL（项目关闭了 Hibernate 自动建表）：

```bash
mysql -u root -p ask_the_wall < src/main/resources/static/client_upgrade.sql
```

全新部署可直接执行 `system_init.sql`（已包含这些表）。`client_upgrade.sql` 可重复执行。

### 部署注意

- SSE 反向代理必须关闭响应缓冲（Nginx `proxy_buffering off;`，并放行
  `text/event-stream`），读超时至少 120 秒；后端每帧为 `data: JSON\n\n`，正常结束发送
  `data: [DONE]`。
- 后端对聊天流设置了 `X-Config-Version` 响应头；客户端刷新能力后可据此判断版本。
- 临时媒体默认保留 24 小时，`client.media.dir` 需可写；媒体清理任务每 10 分钟运行一次。
- 对话正文、录音、参考图不长期保存；恢复记录和任务仅保留 24 小时。
- 服务端只接受官方 OSS `*.oss-*.aliyuncs.com` 下载地址并禁止重定向，不把签名 URL 返回给客户端。