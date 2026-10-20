# 远程模型接口联调结果

日期：2026-10-20。后端：`https://49e02f34.r5.cpolar.top`。使用用户提供的账号获取真实 JWT；不使用调试绕过 Token、不创建 License、不改变后端配置。真实测试使用隔离临时目录，未修改现有智能体或知识库。

## 已完成客户端适配

- 本地服务转发至 `/api/v1/client`，JWT 保留在本地服务内，不传入页面；供应商密钥不参与远程请求。
- capabilities 查询；对话携带 expectedPolicyVersion；向量携带 expectedSpaceId；TTS 携带 expectedConfigVersion。不自动重复提交收费请求。
- 对话与智能体调试共用远程对话；摘要 purpose=summary；发送的 messages 只保留 role/content，移除本机记录字段；流式回答沿用客户端现有格式，取消会提交远程 stop。
- 向量仍在本机建索引，向量计算由后端代理；校验数量、1024 维、有限数值和空间标识。索引空间包含后端地址，避免不同服务的版本标签碰撞。
- 头像沿用本地异步任务接口，后台创建和轮询远程任务；参考图转 JPEG Data URL；产物通过带 JWT 的媒体接口下载、校验并保存到本机 uploads。失败不覆盖原头像。取消不承诺撤销云端费用。
- ASR 转发原始 WAV；TTS 下载后返回本机音频地址。远程媒体仅允许同一后端的 `/api/v1/client/media/{id}`，禁止任意外部 URL 和重定向，避免携带 JWT 访问其他主机。
- Windows 打包清单已加入远程适配模块；实际 Windows 构建及安装未验收。

## 真实验证结果

| 链路 | 结果 |
|---|---|
| capabilities | HTTP 200，四类能力均宣称 available=true |
| 对话 JSON | HTTP 200，返回 choices/usage |
| 客户端流式对话 | HTTP 200，收到增量 `OK` 并保存完整回答 |
| 向量 document/query | HTTP 200，每段返回 1024 维有限数值 |
| 本地文本导入 → 远程向量 → 本机索引 | 一条临时文本从 queued 到 ready |
| 知识库检索 → 远程回答 | 问“展厅几点开放”，命中“展厅每天上午九点开放”，返回“上午九点”，带资料引用 |
| ASR | 有效系统合成测试 WAV 返回 HTTP 200 和 text；中文实录准确率与现场噪声效果仍待实机测试 |
| 头像生成 | 创建 HTTP 202，任务随后进入 error，见下文 |
| TTS | 规范默认音色请求返回 HTTP 503 SERVICE_UNAVAILABLE，见下文 |

## 请后端修复 / 补充

### 1. 头像结果下载失败

仅提交一次真实头像生成。任务 `job-2010c700d52b49c38ddfeeb77d6f35ce`，约北京时间 2026-10-20 16:27:36 创建，随后 running → error：

```json
{"state":"error","error":"模型结果下载失败","code":"SERVICE_UNAVAILABLE"}
```

请根据该任务查供应商产物 URL、下载域名校验、重定向、超时、响应格式及网络连接日志。capabilities available=true 只能说明配置存在，不能证明产物下载可用。未重复提交收费生成；头像媒体下载成功路径和本机持久化使用模拟服务验证，真实结果待后端修复后补验。

### 2. TTS 返回服务不可用

约北京时间 16:33，使用以下格式请求 `/api/v1/client/voice/synthesize`，返回 HTTP 503、code=SERVICE_UNAVAILABLE。客户端保留失败提示，不伪造播放成功。

```json
{
  "requestId":"<每次真实操作生成的 UUID>",
  "text":"你好，欢迎来到展厅。",
  "segmentIndex":0,
  "expectedConfigVersion":"voice-policy-v1"
}
```

请检查供应商合成、音频下载和媒体存储日志。需交付合成 HTTP 200、带认证媒体 GET HTTP 200、可播放 WAV 三步成功结果。

### 3. 音色选择尚未支持

携带 `voice:"Cherry"` 的请求实测 HTTP 400 INVALID_ARGUMENT；去掉 voice 后可进入服务处理，但遇到上一条 503。当前后端文档没有 voice 参数和可选音色列表。客户端默认音色请求不带 voice；选择其他音色会明确提示后端尚未支持，避免将不同音色静默合成为同一声音。

请增加：capabilities.voice.defaultVoice/voices；TTS 可选 voice（Cherry/Serena/Ethan）；音色进入缓存键、幂等参数与分段上下文。交付后解除客户端非默认音色的限制并真实试听验收。

### 4. 游客远程凭据缺失

模型代理需要 JWT。当前不复用退出账号的 Token，不内置管理员账号，不退回本机供应商密钥。因此未登录游客目前无法调用远程对话、向量查询或语音；可查看本机已有内容。原定“游客可以使用配置好的助手和知识库问答”尚未满足。

请提供设备 / 游客凭据获取、权限、续期与撤销方案（独立于工作人员登录），或由后端明确交付安全的匿名受限接口。License 仍未接入，不能用前端假身份替代。

## 尚未宣称完成

真实头像下载成功、TTS 播放成功、多音色、游客 TTS 播放、License、现场麦克风及扬声器通话体验、Windows 安装验收。测试凭据和 JWT 不写入本文或仓库。

## 客户端检查

全量自动化 99/99 通过；最后音频完整性与缓存修正后，相关 14 项测试通过；语法检查和 diff 空白检查通过。浏览器内真实登录后发送欢迎语，收到远程生成的完整中文回答。自动化涵盖任务取消、停止生成、凭据不发往外站、向量校验、头像结果本机持久化及失败不覆盖。

## 后端处理结果（2026-10-20）

### 1. 头像 / TTS 下载失败：已修复

根因：供应商返回的 OSS 签名 URL 在统一升级为 https 时被重新构造，`%2B`、`%2F`、`%3D`
被解码成 `+`、`/`、`=`，OSS 校验签名失败后返回 403，后端统一映射为
`SERVICE_UNAVAILABLE`。下载失败是间歇性的，取决于当次签名是否包含这些字符。

修复：只校验协议/域名/端口/凭据，保留原始路径与查询串，仅替换 scheme 为 https。
TTS 下载失败时增加 host/status/content-type 安全日志。

已验证（本地 8089，真实供应商）：

- TTS `Cherry`、`Serena` 均返回 200，响应含 `voice`，媒体 GET 200 且为可解码 WAV；
- 头像任务 `job-978f6cba632d44c086fa32dd602c1eef` 202 → `complete`，
  媒体 GET 200，2048×2048 PNG（5,411,131 字节）。

### 2. 音色选择：已支持

- `capabilities.voice` 新增 `defaultVoice` 与 `voices`（当前 `Cherry` / `Serena` / `Ethan`）。
- `/voice/synthesize` 新增可选 `voice`；缺省用 `defaultVoice`，不在列表返回
  400 `INVALID_ARGUMENT`，响应回显实际使用的 `voice`。
- 音色已加入幂等哈希与媒体缓存键；`voice.configVersion` 在音色列表/默认音色变化时更新。
- 默认音色请求可继续不带 `voice`，现有客户端无需改动。

### 3. 免 JWT 对话接口：已实现

未建立独立游客账号或设备注册流程。`/capabilities`、`/chat/completions`、
`/chat/{generationId}/stop`、`/requests/{requestId}`、`/voice/recognize`、
`/voice/synthesize`、`/media/{id}` 以及 `textType=query` 的 `/embeddings`
均不再要求 JWT。未登录请求使用固定匿名归属；`textType=document` 的向量、头像创建与
头像任务查询仍要求 JWT。

`license.enforce=true` 时，对话、语音和向量生成仍要求 `X-License`，License 约束不变。
匿名 `requestId` 必须全局唯一且不可预测（建议 UUID），避免与其他匿名请求冲突或重放。

已验证（本地 8089，新构建）：

- 无 `Authorization` 调用 `/capabilities` 200；
- 无 `Authorization` 调用非流式 `/chat/completions` 200，随后 `/requests/{id}` 200、
  `/chat/{generationId}/stop` 200 `state=completed`；
- 无 `Authorization` 的 `textType=query` 向量请求进入业务校验（409
  `INDEX_SPACE_CHANGED`），`textType=document` 返回 401；
- 无 `Authorization` 访问他人媒体返回 404，头像接口返回 401；
- 一次无 JWT TTS 请求因供应商 90 秒超时返回 504 `UPSTREAM_TIMEOUT`，非鉴权问题。

### 部署提示

修复已包含在新构建产物中，需要重启 8088 服务后前端再复测。上面第 1、2、3 条已用新构建在
8089 端口完成真实验证；参考图头像分支与取消分支仍未覆盖。
