# 客户端服务 API 实现规范

文档修订：2026-10-22。接口契约版本：1.3。适用对象：独立服务端开发与联调人员。本文可以独立阅读，不需要客户端源码。路径、字段与配套 JSON 示例保持一致。本次修订增加服务端内部联网搜索能力；客户端请求/响应结构不变。

配套材料：[模型供应商接入规范](backend-model-provider-reference.md)、[完整请求/响应示例](backend-api-examples.json)。示例均为合成数据，不含真实密钥。尚未确定的事项见第 13 节。

## 1. 服务端职责与接口总览

服务端负责用户登录、License 兑换、设备授权、模型配置、调用模型供应商、取消生成、短期结果恢复和临时媒体下载。客户端负责对话历史、智能体、知识库原文、文档解析、检索、头像设置及业务数据的本地保存。

客户端提交的 messages 已包含智能体提示词、历史和需要引用的知识片段；服务端直接执行模型调用。服务端不需要根据会话 ID、智能体 ID 或知识库 ID 查询客户端业务数据。

所有路径统一以 `/api/v1/client` 为前缀。下表及后续标题省略该前缀，例如 `/chat/completions` 的完整路径为 `/api/v1/client/chat/completions`。

| 方法 | 路径 | 服务端功能 |
| --- | --- | --- |
| GET | /capabilities | 返回账号可用能力、输入限制和配置版本 |
| POST | /chat/completions | 普通对话、智能体、翻译、调试、摘要共用模型调用 |
| POST | /chat/:generationId/stop | 停止同一次生成组的摘要与回答 |
| GET | /requests/:requestId | 查询模型请求结果，避免断线后重复付费 |
| POST | /voice/recognize | WAV 录音识别为文字 |
| POST | /voice/synthesize | 将回答中的指定片段合成为语音 |
| POST | /avatar/generate | 创建静态头像生成任务 |
| GET | /image-jobs/:id | 查询头像任务 |
| DELETE | /image-jobs/:id | 取消头像任务 |
| POST | /embeddings | 将文档片段或问题批量转为向量 |
| GET | /media/:id | 下载本用户生成的图片/音频 |
| POST | /auth/login | 登录 |
| POST | /auth/refresh | 刷新登录凭据 |
| POST | /auth/logout | 退出并撤销会话 |
| GET | /session | 查询账号、License 和设备授权状态 |
| POST | /licenses/redeem | 兑换 License |
| POST | /devices/activate | 激活当前设备并签发绑定设备的凭据 |
| POST | /devices/current/refresh | 检查当前设备授权 |
| POST | /devices/current/deactivate | 解除当前设备绑定 |

模型配置仅对超级管理员开放，不向普通客户端下发供应商地址或密钥。管理后台的配置 CRUD 不属于本文 19 项客户端接口，须单独约定。

### 1.1 免 JWT 接口

以下对话、语音与只读接口不要求 `Authorization`。未登录调用按匿名归属处理；`license.enforce=true` 时，生成类接口仍需 `X-License`，头像接口始终要求 JWT。免 JWT 不代表免限流，部署时应在网关或应用层按设备/IP 限流。

| 方法 | 路径 | 免 JWT | 说明 |
| --- | --- | --- | --- |
| GET | /capabilities | 是 | 游客可先取能力与版本 |
| POST | /chat/completions | 是 | 普通对话、智能体、摘要 |
| POST | /chat/:generationId/stop | 是 | 停止当前回答 |
| GET | /requests/:requestId | 是 | 恢复当前发起方的请求 |
| POST | /voice/recognize | 是 | 语音识别 |
| POST | /voice/synthesize | 是 | 语音合成 |
| GET | /media/:id | 是 | 获取临时音频/图片 |
| POST | /embeddings（textType=query） | 是 | 游客问题检索既有本地索引 |
| POST | /embeddings（textType=document） | 否 | 登录用户导入资料、建立索引 |
| POST | /avatar/generate | 否 | 登录后生成数字人形象 |
| GET/DELETE | /image-jobs/:id | 否 | 登录用户查看/取消头像任务 |

匿名请求没有账号身份，服务端按固定匿名归属保存请求记录与媒体。客户端必须使用全局唯一且不可预测的 requestId（建议 UUID），并且不得复用其他账号的 requestId；媒体 ID 为随机值，在有效期内可下载。

## 2. 公共约定

- 生产环境 HTTPS。除 1.1 节列出的免 JWT 接口外，认证使用 `Authorization: Bearer <accessToken>`，实际凭据由独立后端发放，不能使用供应商 API Key。设备绑定通过服务端会话校验，具体采用第 10 节的激活后绑定 token 方案，不能仅信任裸机器码。客户端内部令牌不能作为远程身份凭据。
- JSON 成功响应直接返回业务对象，不额外包 `{data: ...}`，以保证统一解析。错误保留字符串 error，同时增加稳定 code。
- 时间统一为北京时间（UTC+8），ISO 8601 格式带 `+08:00`。ID 使用不透明字符串；客户端不依赖数据库数值 ID。所有 maxChars 均按 JavaScript UTF-16 code units 计算（emoji 等可能计为两个单位），后端其他语言不能直接按字节限制。空间、配置版本的具体字符串都只是示例，必须当不透明值比较。本期支持的能力仅限本文列出的接口。
- 请求幂等 requestId 最多 100 字符；JSON 可放请求体，二进制录音使用 `X-Request-ID`。若头和体同时传且不一致，返回 400。
- 幂等键统一为（userId、requestId），跨本期所有模型接口保持唯一。记录 operation 与规范化请求体摘要；同一 ID 换接口、换参数均返回 409 REQUEST_CONFLICT。处理中重复提交返回 409 REQUEST_IN_PROGRESS；终态重复提交重放既有响应，不再次付费。所有操作都能用 GET /requests/:requestId 恢复，见第 4.5 节。
- 不自动重试任何可能付费的生成。幂等记录须跨后端重启可用。第一期统一约定：终态请求记录和可恢复结果至少保留至结束后 24 小时；图片/音频成功结果也至少保留至成功后 24 小时。expiresAt 返回实际过期时间，实际保留期不得短于承诺。运行中的任务不得因记录 TTL 提前清除。
- 严格禁止普通用户通过 model/baseUrl/apiKey/region 等字段覆盖管理配置；若请求包含这些禁止字段，建议返回 400。
- 图片/音频/请求状态/停止操作都验证归属。已登录请求按 userId 隔离；1.1 节免 JWT 请求按匿名归属隔离，因此匿名 requestId 必须全局唯一且不可猜测。并发上限由服务端按用户和授权控制，不能将所有用户串行为单个任务。
- 建议操作时限：对话 300 秒、图片 180 秒、语音 90 秒、向量每批 60 秒。服务端与反向代理须协调超时；这些时限不代表模型响应速度保证。

统一错误示例（HTTP 429）：

```json
{
  "error": "请求过于频繁，请稍后重试。",
  "code": "RATE_LIMITED",
  "requestId": "req-example-001",
  "retryAfterSeconds": 10
}
```

至少区分：400 INVALID_ARGUMENT/CONTEXT_TOO_LARGE；401 INVALID_CREDENTIALS/AUTH_EXPIRED/SESSION_REVOKED；403 ACCOUNT_DISABLED/LICENSE_REQUIRED/LICENSE_EXPIRED/DEVICE_REVOKED/DEVICE_NOT_ACTIVATED/DEVICE_MISMATCH/DEVICE_LIMIT_REACHED；404 REQUEST_NOT_FOUND/JOB_NOT_FOUND/RESOURCE_NOT_FOUND；409 REQUEST_IN_PROGRESS/REQUEST_CONFLICT/INDEX_SPACE_CHANGED/CONFIG_CHANGED；410 REQUEST_EXPIRED/RESOURCE_EXPIRED；413 PAYLOAD_TOO_LARGE；422 CONTENT_REJECTED/NO_SPEECH/NO_READABLE_TEXT；429 RATE_LIMITED；503 SERVICE_UNAVAILABLE；504 UPSTREAM_TIMEOUT。

请求处理顺序：校验身份/归属（免 JWT 接口允许匿名，匿名请求使用固定匿名归属）→ 查同 userId/requestId 的幂等记录并重放 → 新请求才校验当前授权、请求限制、配置版本 → 原子登记 → 调用供应商。对新请求的版本不一致、参数校验等失败不能触发上游。记录原 operation、原终态状态码和结果，不能把供应商成功但返回链路失败当作可重新执行的新请求。

所有 JSON 响应与实际下载使用 Cache-Control: no-store（应用自己做按账号隔离的受控缓存）；不将应用凭据或原文写入普通接入日志。模型输入仅在处理期间使用，短期恢复结果与任务资源按到期清理，不长期保存对话/知识库正文。

供应商鉴权错误不得伪装成用户登录失效 401；使用 503 SERVICE_UNAVAILABLE 等安全业务错误，不回显供应商原始响应和密钥。业务额度不足可使用 403 QUOTA_EXCEEDED，与供应商故障区别。

同步调用 /chat/completions、/voice/recognize、/voice/synthesize、/embeddings 的连接中断时，后端尽量取消对应上游并将请求登记为 stopped 或真实终态，不能让已确认成功的请求被覆盖成失败。图片异步任务不同：创建事务中先持久登记 requestId 和 jobId；成功接受后，即使创建响应丢失也保留该任务，通过 /requests/:requestId 找回；后续只由 DELETE /image-jobs/:id、撤销策略或任务超时取消，不因 202 响应连接正常关闭就终止。

## 3. GET /capabilities

返回模型调用所需的公开运行限制与缓存版本。不能返回供应商地址、密钥或底层模型列表。

响应示例：

```json
{
  "chat": {
    "available": true,
    "contextBudget": 262144,
    "reservedOutputTokens": 8192,
    "maxOutputTokens": 8192,
    "spaceId": "chat-policy-v3",
    "maxRequestBytes": 4194304,
    "maxMessages": 1024,
    "maxResponseChars": 200000,
    "webSearchAvailable": true,
    "maxToolRounds": 4
  },
  "image": {
    "available": true,
    "maxPromptChars": 3000,
    "maxReferenceBytes": 20971520,
    "maxRequestBytes": 33554432,
    "configVersion": "image-policy-v2"
  },
  "voice": {
    "available": true,
    "recognitionAvailable": true,
    "synthesisAvailable": true,
    "maxRecordingSeconds": 60,
    "maxRecordingBytes": 1920044,
    "maxTextChars": 32000,
    "defaultVoice": "Cherry",
    "voices": ["Cherry", "Serena", "Ethan"],
    "configVersion": "voice-policy-v4",
    "segmentationVersion": "speech-segments-v1"
  },
  "embedding": {
    "available": true,
    "spaceId": "embedding-space-v2",
    "dimensions": 1024,
    "maxBatchSize": 10,
    "maxTextChars": 4000,
    "maxResponseBytes": 2097152
  }
}
```

available 综合账号授权、业务启停和配置是否完整；没有权限返回 false，不让客户端猜测。该接口不执行付费模型连通测试，不能保证供应商此刻一定可调用；真实调用失败仍返回安全错误；不能返回演示答案或静默切换未约定的模型来伪装成功。voice.available 表示识别/合成至少一项可用，两个操作分别以 recognitionAvailable/synthesisAvailable 判断，不能仅用总开关误开启已禁用的操作。voice.defaultVoice 与 voice.voices 是服务端允许的音色列表；客户端只能从 voices 中选择，缺省使用 defaultVoice。chat.spaceId 在影响上下文摘要策略的模型/预算变化时更新；用于让客户端判断摘要缓存是否仍适用，不暴露底层模型名称。voice.configVersion 在影响音频内容的模型、音色列表或默认音色变化时更新，避免命中旧音色缓存。chat.webSearchAvailable 表示服务端已启用并在当前授权下可使用联网搜索，不代表客户端可以直接调用搜索接口；chat.maxToolRounds 是服务端单次回答允许的最大工具轮数。客户端不得因 webSearchAvailable=false 自行拼接“请联网”指令，服务端会按自身能力决定是否调用工具。

第一期在线调用模式下，/session 和 /capabilities 允许已登录但尚未激活/授权过期的用户访问，返回状态与 available=false，不因缺 License 阻止激活界面初始化。各个生成接口仍独立强制验证授权，available 不能代替鉴权。

客户端登录、成功激活、重新打开应用、恢复网络、发起新业务操作前，以及收到 CONFIG_CHANGED/INDEX_SPACE_CHANGED 后刷新 /capabilities。可做短期缓存，但不能永久缓存；已有播放/向量索引任务固定使用开始时的版本。

contextBudget 是该策略允许的总上下文预算；本地输入预算=contextBudget-reservedOutputTokens。后端保证 maxOutputTokens<=reservedOutputTokens，真实 token 校验仍由后端执行，超出返回 400 CONTEXT_TOO_LARGE 而非静默截断。仅发生密钥轮换时无需让 chat.spaceId/voice.configVersion 失效；影响行为/分段或底层模型的变更必须更新对应版本。

embedding.spaceId 是后端生成的稳定、不透明向量空间标识，在模型/维度/语义相关参数变化时更新；不应因普通密钥轮换无意义地变化。本地索引必须按后端来源、账号和 spaceId 隔离。

## 4. POST /chat/completions：普通对话、智能体、调试、摘要共用

### 4.1 传入已经整理好的 messages

客户端提交已经组合好的 system + user/assistant 消息。服务端按后台配置选择模型并调用供应商，不重复组合消息或检索知识库。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| requestId | string | 是 | 本次远程调用唯一标识；摘要的每一批也是独立调用 |
| generationId | string | 是 | 同一用户点击“发送/重试”的生成组 ID，摘要和最终回答共用；最多 100 字符 |
| purpose | string | 是 | answer 或 summary；只区分组内任务和限额，不改变模型权限 |
| messages | array | 是 | 按顺序传入 `{role,content}`；role 为 system/user/assistant，content 为纯文本 |
| stream | boolean | 是 | 普通回答通常为 true，摘要调用为 false；兼容非流式回答 |
| expectedPolicyVersion | string | 是 | capabilities.chat.spaceId，摘要和该组最终回答使用同一版本 |
| max_tokens | integer | 否 | 摘要使用 768；最终回答可不传，后端使用配置上限。仅作为预算请求，低于 256 时后端按 256 执行 |

messages 至少 1 条，最多按 capabilities.chat.maxMessages；content 必须是非空字符串，保留用户原始空白，不把它当 JSON 指令解析。第一条为 system，后续仅 user/assistant，最后一条为本次 user；本期不接受客户端提交图片 content 数组或 tools/function calling。整个 JSON body 不超过 capabilities.chat.maxRequestBytes。max_tokens 存在时是正整数且不超过 maxOutputTokens，超限明确返回 400；低于 256 时按 256 执行，避免模型只返回空内容。

服务端可在内部使用联网搜索工具：当 purpose=answer 且 capabilities.chat.webSearchAvailable=true 时，模型可自行决定调用 web_search 或 web_fetch，服务端最多执行 maxToolRounds 轮，工具结果仅用于生成最终回答，不通过客户端 API 暴露工具调用帧、搜索结果或供应商原始响应。摘要请求（purpose=summary）不启用工具。联网回答可能包含多次上游调用，客户端仍按同一 requestId/generationId 处理完成、失败与停止；usage 若返回则为各轮汇总值。是否联网由服务端和模型判断，客户端不能通过请求字段强制联网或指定搜索词。

purpose=summary 时 stream 必须为 false，max_tokens 默认 768 且受后端策略上限约束；answer 同时支持 stream=true/false。purpose 只影响分组和额度检查，不能用来选择任意供应商或绕过授权。

不新增 content/systemPrompt/knowledgeIds/agentId 等重复输入；知识片段已在 system.content 中。客户端不能指定 model。后端不可擅自增加聊天角色提示词覆盖用户的智能体配置，但必须执行系统安全与额度约束。

请求示例：

```json
{
  "requestId": "req-answer-001",
  "generationId": "gen-001",
  "purpose": "answer",
  "expectedPolicyVersion": "chat-policy-v3",
  "stream": true,
  "messages": [
    {"role": "system", "content": "你是一位专业翻译。将用户内容翻译成英文，保留原意。"},
    {"role": "user", "content": "很高兴与你合作。"}
  ]
}
```

### 4.2 非流式响应（200 application/json）

返回 choices 结构：

```json
{
  "requestId": "req-answer-001",
  "choices": [
    {
      "index": 0,
      "message": {"role": "assistant", "content": "It is a pleasure to work with you."},
      "finish_reason": "stop"
    }
  ],
  "usage": {"prompt_tokens": 40, "completion_tokens": 12, "total_tokens": 52}
}
```

choices[0].message.content 必须是非空字符串；usage 可选，但存在时为真实数值；发生联网工具调用时，usage 为各轮供应商返回值的汇总。finish_reason 支持 stop/length，length 表示达到输出长度限制。审核拒绝使用明确错误，不能伪造正常回复。

### 4.3 流式响应（200 text/event-stream）

增量字段固定为 choices[0].delta.content，不能改为其他结构。

```text
data: {"choices":[{"index":0,"delta":{"content":"It is a pleasure"},"finish_reason":null}]}

data: {"choices":[{"index":0,"delta":{"content":" to work with you."},"finish_reason":null}]}

data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":40,"completion_tokens":12,"total_tokens":52}}

data: [DONE]

```

每帧 `data: JSON\n\n`，UTF-8。不要输出思考内容作为回答正文；增量必须是字符串，空字符串可忽略。建议在含 finish_reason 的最后一帧携带 usage，避免 [DONE] 后才追加用量。完整响应必须有 finish_reason 和最终 [DONE]，不能靠断开连接代表正常完成。

代理关闭对应响应缓冲，及时 flush；连接头与代理配置匹配部署环境。允许注释心跳 `: ping\n\n`，不能把心跳放 delta 正文。联网工具的中间调用不作为独立 SSE 事件输出；客户端只接收最终回答的 `delta.content`。

如果流开始前鉴权失败，返回相应 HTTP 状态与 JSON 错误。流已经开始后，返回错误帧并结束，不发送正常完成标记：

```text
data: {"error":"生成失败，请重试。","code":"UPSTREAM_ERROR","requestId":"req-answer-001"}

```

客户端负责保存消息、失败状态和引用来源；服务端仅返回本节定义的流式事件，不持久化业务对话。

### 4.4 摘要和停止

长对话摘要使用同一模型的非流式调用：system 描述摘要任务，user.content 为已序列化的 previousSummary 与 conversationExcerpt；purpose=summary、stream=false、max_tokens=768；最终回答 purpose=answer。不要另加摘要接口。purpose 用来识别哪次请求是该组最终回答，不能单靠 stream 推断，因为非流式回答同样合法。

一个 generationId 下可能有多个摘要 requestId，再有最终回答 requestId。重试/重新生成使用新 generationId；消息替换仍在本地完成。

首次执行新 requestId 前校验 expectedPolicyVersion。版本变化则 409 CONFIG_CHANGED，不调用模型；本地停止该组、刷新能力并明确让用户重新发起，不自动重跑已经付费的摘要。已接受的单次上游调用使用配置快照，不在流中途切换模型。已有 requestId 的结果重放先于当前配置版本检查，避免配置更新导致已付费结果无法恢复；身份与资源归属检查始终执行。建议响应头 X-Config-Version 返回实际策略版本。

`POST /chat/:generationId/stop` 请求体 `{}`；建议响应：

```json
{"ok": true, "generationId": "gen-001", "state": "stopped"}
```

实际取消该账号该生成组正在执行的上游调用；停止后禁止同组迟到的新摘要/最终调用继续发起。保存短期取消标记。重复停止幂等；最终回答已完成可返回 state=completed。即使该组首个调用还没到达，也在通过身份校验后记录短期取消标记并返回 stopped，阻止“停止先到、请求后到”的竞态。取消标记至少保留 24 小时；generationId 不得复用。客户端负责中止等待并保存已收到的片段。generationId 不是本地业务 ID 的远程数据库引用，后端仅用来归组请求；不能因一个摘要调用结束就认定整个生成组 completed。停止响应 state=completed 只用于已明确知道最终回答完成的组，否则仍登记 stopped。

远程请求断开时也需要取消该次上游请求；只返回停止成功但让上游继续生成，不符合要求。不能承诺取消一定撤销供应商费用。

### 4.5 GET /requests/:requestId：所有模型调用的统一恢复

统一查询五类模型操作的状态与结果。客户端在发送前保存 requestId，响应丢失后用同一 ID 查询，不重新创建付费任务。

```json
{
  "requestId": "req-answer-001",
  "operation": "chat",
  "generationId": "gen-001",
  "state": "completed",
  "httpStatus": 200,
  "result": {
    "choices": [{"index": 0, "message": {"role": "assistant", "content": "It is a pleasure to work with you."}, "finish_reason": "stop"}]
  },
  "error": null,
  "code": null,
  "expiresAt": "2026-10-20T16:00:00+08:00"
}
```

operation 枚举 chat/voice-recognize/voice-synthesize/avatar-generate/embeddings。state 枚举 running/completed/stopped/failed。成功查询本身返回 HTTP 200；httpStatus 表示原调用的最终状态，running 时为 null。result 是该接口原本会返回的成功 JSON 对象，不做第二套格式；failed/stopped 时 result=null，失败带字符串 error 与稳定 code。running 时 result/error/code 均为 null，expiresAt 可为 null。

| operation | completed 的 result |
| --- | --- |
| chat | choices、usage 等非流式完整回答；流式回答也还原为这一结构 |
| voice-recognize | `{text, requestId}` |
| voice-synthesize | `{url, segmentIndex, segmentCount, voice, cached, configVersion, expiresAt}` |
| avatar-generate | 原 HTTP 202 的 `{job}`；这里只表示成功创建任务，不表示图片已生成 |
| embeddings | `{vectors, dimensions, spaceId, usage, requestId}` |

stopped 可以额外带 partialContent，但不能作为正常完成。头像创建请求的 result 中保存 jobId 后，仍通过 /image-jobs/:id 查看最终生成状态。通用记录和图片任务是两种对象，不能把两种状态混在一起。

恢复规则：HTTP 请求超时/中断后先查询 requestId。running 只轮询状态；completed 使用原结果；failed/stopped 不自动重试，用户选择重试时使用新 ID。404 REQUEST_NOT_FOUND 只说明服务端尚未记录此 ID，不证明从未发生调用；需要重传时只能使用相同 ID 和相同请求体，不能换 ID 猜测重试。超过约定保留期停止自动恢复；若服务端保留过期标记返回 410 REQUEST_EXPIRED，否则可返回 404。客户端保存发送时间，不因 404 无限制重传。

语音/向量已完成响应重放返回原结果。临时媒体内容仍有效时可刷新短期下载签名而不重新生成；资源已经清理则返回 410 RESOURCE_EXPIRED，不能暗中再调用模型。状态和结果仅短期保留，不建设云端对话/知识库业务数据库。

## 5. POST /voice/recognize

请求采用原始 WAV 二进制：

```http
POST /api/v1/client/voice/recognize
# Authorization: Bearer <可选；游客可省略>
Content-Type: audio/wav
X-Request-ID: req-asr-001

<WAV 二进制>
```

| 项目 | 要求 |
| --- | --- |
| 编码 | RIFF/WAVE、PCM 16-bit、有符号小端 |
| 采样率 | 16000 Hz |
| 声道 | 1 |
| 时长 | 0.1–60 秒 |
| 最大字节数 | 1,920,044 字节（44 字节头 + 最长 PCM） |

先校验格式、长度、有效声音，再请求供应商。客户端只在主动录音后发送，不自动保存原录音。

响应（200）：

```json
{"text": "展馆几点开门？", "requestId": "req-asr-001"}
```

text 为去掉首尾空白的非空字符串，上限 16000 字符。无有效音频采用 422 NO_SPEECH。无法识别返回 422 NO_SPEECH 或相应错误，不用空字符串假装成功。不自动发送对话，前端会追加到当前草稿，由用户核对后发送。

不要求本期改成实时语音识别或 WebSocket。

## 6. POST /voice/synthesize

接收整条回答文本与需要合成的片段索引，不需要服务端保存消息或提供 messageId。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| text | string | 是 | 整条回答，可能含 Markdown，最多 32000 字符 |
| segmentIndex | integer | 否 | 从 0 开始，默认 0；越界返回 400 |
| voice | string | 否 | 来自 capabilities.voice.voices，缺省使用 defaultVoice；不在列表返回 400 |
| requestId | string | 是 | 当前片段请求的幂等标识；同一 ID 换音色视为不同参数 |
| expectedConfigVersion | string | 是 | 来自 capabilities.voice.configVersion，防止同一次播放期间切换音色 |

```json
{
  "text": "你好！展馆每天上午九点开放。",
  "segmentIndex": 0,
  "voice": "Cherry",
  "requestId": "req-tts-001-0",
  "expectedConfigVersion": "voice-policy-v4"
}
```

服务端对全文按固定规则清理与分段：跳过代码、图片、HTML 与 URL，首段最多 120、后续最多 500 个 UTF-16 单位。完整规则见《模型供应商接入规范》第 5 节，无需参考客户端代码。segmentationVersion 标识该算法版本；同一文本与版本的片段数量和内容必须稳定。客户端逐次递增 segmentIndex，直到 segmentCount。

响应（200）：

```json
{
  "url": "/api/v1/client/media/audio-example-001",
  "segmentIndex": 0,
  "segmentCount": 1,
  "voice": "Cherry",
  "cached": false,
  "configVersion": "voice-policy-v4",
  "expiresAt": "2026-10-20T16:00:00+08:00"
}
```

url 是后端可鉴权下载的临时音频地址；必须只生成本次请求的片段，不预先将整篇全部付费合成。voice 回显本次实际使用的音色；请求未带 voice 时为 defaultVoice。cached 为服务端缓存是否命中，缓存键包含音色。配置版本变化返回 409 CONFIG_CHANGED，不能把不同音色片段拼在一次播放里。后端实际分段和响应中的 segmentCount 在该版本下必须稳定，segmentIndex 不自动取模或夹到最后一段。segmentationVersion 变化也必须改变 configVersion，防止段数和段落顺序变动却被当成同一版本。text 清理后无可读文字返回 422 NO_READABLE_TEXT。

下载输出：完整可解码 WAV，PCM 16-bit，1 或 2 声道；采样率允许 16000/22050/24000/44100/48000 Hz。建议 audio/wav；单段不超过 16 MiB。RIFF/data 长度必须与实际字节一致，如供应商返回 WAV 占位长度，服务端先规范化；具体条件见《模型供应商接入规范》第 5 节。

客户端负责下载、播放和本地缓存。缓存按账号、后端来源、configVersion、分段版本、音色和文本隔离。普通请求不得带 model 覆盖；voice 只能取 capabilities.voice.voices 中的值。

客户端中止请求时取消上游与下载，停止后不请求下一片段；自动朗读开关继续保存在本地。本期语音接口采用同步请求响应。

## 7. POST /avatar/generate 与图片任务

### 7.1 请求

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| prompt | string | 是 | 去首尾空白后非空，最多 3000 字符 |
| image | string | 否 | 参考图 Data URL；纯文字生成省略或传空字符串 |
| requestId | string | 是 | 幂等标识 |

文字生成：

```json
{"prompt": "亲切的讲解员，短发，白衬衫，浅蓝背景。", "requestId": "req-image-001"}
```

image 是图像内容的 Base64 Data URL，不是本地路径或公网 URL。服务端不需要单独提供长期参考图上传接口。

客户端提交已转成 JPEG 的 `data:image/jpeg;base64,...`，透明背景已铺白。服务端校验 Base64、实际 JPEG 格式、宽高各 240–8000、宽高比不超过 8:1，解码后最多 20 MiB。该接口 JSON body 上限 32 MiB，代理须容纳 Base64 开销。不接受本地路径或用户指定的下载 URL。

生成单张静态头像，分辨率参数为 2K；普通用户不传 model、size、n、region、workspaceId 或 apiKey。头像名称、位置、同步和“确认使用”仍是本地逻辑。

### 7.2 创建响应（202）

```json
{
  "job": {
    "id": "image-job-example-001",
    "state": "queued",
    "message": "等待生成",
    "createdAt": "2026-10-19T16:00:00+08:00"
  }
}
```

### 7.3 GET /image-jobs/:id

查询直接返回 job 对象，不额外包 job 字段：

```json
{
  "id": "image-job-example-001",
  "state": "complete",
  "message": "形象已生成，请预览并确认使用",
  "createdAt": "2026-10-19T16:00:00+08:00",
  "url": "/api/v1/client/media/image-example-001",
  "expiresAt": "2026-10-20T16:00:00+08:00"
}
```

**状态枚举为 queued/running/downloading/complete/error/cancelled。** running/downloading 只表示真实阶段，无估算百分比。error 终态带字符串 error 和稳定 code（如 CONTENT_REJECTED/UPSTREAM_TIMEOUT），message 同样可展示；不返回供应商原始错误。

创建和查询响应可返回 pollAfterMs，默认 1000；客户端不得早于 Retry-After 指定时间再次查询。读取状态不触发新生成，应用重连后仍可查询已提交任务；实际状态与临时结果保留期必须明确。

complete 仅在后端已取得并校验结果后返回。图片结果为真实 PNG、单张不超过 20 MiB、尺寸各不超过 8192 且总像素不超过 33554432；不能只返回供应商不稳定的临时结果。使用后端可鉴权的临时资源即可，不要求长期业务图片云存储。

客户端在 complete 后下载 PNG 并保存，供头像展示及离线预览。服务端 jobId 为不透明标识，不承担客户端文件名或业务头像 ID 的含义。

### 7.4 DELETE /image-jobs/:id

返回取消后的同结构 job。只有 queued/running/downloading 进入 cancelled；已 complete/error/cancelled 保持终态。取消尽量停止上游等待和下载，但不能承诺撤销云端计费。取消和失败均不覆盖本地已保存头像。

## 8. POST /embeddings

客户端已完成文档分段；服务端只负责文本转向量，不重复切块，不实现知识库 CRUD 或相似度检索。

textType=query 免 JWT，可按匿名归属调用；textType=document 必须携带 JWT，未登录返回 401 AUTH_EXPIRED。两种类型都在写入幂等记录前校验 textType，非法值返回 400 INVALID_ARGUMENT。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| texts | string[] | 是 | 1–10 段；每段非空且最多 4000 字符；document 通常每段最多 360 字符 |
| textType | string | 是 | document 或 query，映射供应商的 document/query 类型 |
| expectedSpaceId | string | 是 | 来自 capabilities.embedding.spaceId，必须与当前生效空间一致 |
| requestId | string | 是 | 该批次幂等标识 |

```json
{
  "texts": ["展馆每天上午九点开放。", "咖啡馆售卖饮品。"],
  "textType": "document",
  "expectedSpaceId": "embedding-space-v2",
  "requestId": "req-embedding-001"
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| vectors | number[][] | 与 texts 一一对应，长度与顺序完全一致 |
| dimensions | integer | 第一期固定 1024；每条向量必须恰好为此长度 |
| spaceId | string | 实际使用的向量空间，必须等于 expectedSpaceId |
| usage | object | 可选，真实用量，例如 total_tokens |
| requestId | string | 请求标识 |

为了避免文档中写成无效的截断 JSON，完整 1024 维合成示例放在配套 backend-api-examples.json。不能真的返回 `[0.1, 0.2, "..."]`。

后端处理供应商乱序 text_index，返回客户端的 vectors 已排序。向量均为有限数值且范数有限、非零；行数不符、维度不符、重复索引或空向量返回错误，不保存部分成功批次作为完整索引。

第一期兼容边界：返回的 dense 向量固定 1024 维，管理端只发布客户端已支持的模型/维度组合。1024 维是本期固定协议；未来变更维度须同步升级协议和客户端，不能只修改 capabilities。

模型由后台配置。调用时 expectedSpaceId 不一致返回 409 INDEX_SPACE_CHANGED，**不要自动用新空间继续处理**。本地只在整库成功且空间未变时发布索引。模型变化后由用户明确重建，失败和轮询不触发自动付费重试。

查询请求与文档向量采用同一空间。查询问题长度最多 4000 字符，超长报错，不静默截断。API 不提供原始向量 JSON 上传/知识库管理接口，因为数据继续在本地。

## 9. 图片、音频临时下载

url 推荐返回相对远程 API Origin 的 `/api/v1/client/media/:id`，本地服务带应用身份下载。后端验证资源归属，返回正确 Content-Type、Content-Length，音频支持必要的 Range/206/416，清理后的资源返回 410 RESOURCE_EXPIRED；无权限/不存在按统一错误策略返回，不泄露他人资源是否存在。

若改用短期签名 HTTPS 地址，必须由后端返回并限定可信来源，不能把应用 Bearer 凭据转发给供应商/对象存储；重定向同样不可泄露凭据。无关第三方 URL 或客户端提供的 URL 不能作为下载目标。

客户端负责下载后的本地展示，服务端无需了解客户端播放或图片展示实现。

供应商结果的安全下载与 WAV 规范化由服务端负责，客户端只接收合规的媒体文件；具体要求见《模型供应商接入规范》。

## 10. 登录与设备授权

采用**服务端会话绑定设备**：客户端只发送应用 accessToken；激活后由服务端换发绑定 activationId 的访问/刷新凭据，不额外传 deviceToken。

### 10.1 请求与响应

| 方法 | 路径 | 请求关键字段 | 响应关键字段 |
| --- | --- | --- | --- |
| POST | /auth/login | account、password、deviceFingerprint、fingerprintVersion、appVersion | tokenType、accessToken、accessTokenExpiresAt、refreshToken、refreshTokenExpiresAt、user、activationId |
| POST | /auth/refresh | refreshToken | 轮换后的同结构凭据；保留会话绑定的 activationId |
| POST | /auth/logout | refreshToken；Authorization 带当前 accessToken | `{ok:true}`，撤销本会话及其刷新链 |
| GET | /session | Authorization | user、license、deviceAuthorization、serverTime |
| POST | /licenses/redeem | licenseKey、requestId；Authorization | `{license}`，不自动代替设备激活 |
| POST | /devices/activate | deviceFingerprint、fingerprintVersion、deviceName、platform、appVersion、requestId；Authorization | 与登录同结构的新凭据；activationId 非空 |
| POST | /devices/current/refresh | `{}`；Authorization | `{deviceAuthorization}`；在线检查，不要求额外设备密钥 |
| POST | /devices/current/deactivate | requestId；Authorization | `{ok:true}`；释放本机绑定并撤销该激活会话，之后重新登录 |

登录 account/password 是必填非空字符串；account 最多 128 字符、password 最多 128 字符，不在登录接口执行新密码创建规则。deviceFingerprint 是双方约定算法的 64 位十六进制摘要，fingerprintVersion 非空且最多 32 字符，appVersion 最多 64 字符。activate 额外 deviceName 最多 80 字符，platform 第一阶段支持 `win32-x64`。requestId 规则同公共约定。licenseKey 非空、最多 256 字符，保留后端定义的字符和大小写语义；具体发行格式由后端确定。

登录成功示例（尚未绑定设备）：

```json
{
  "tokenType": "Bearer",
  "accessToken": "example-access-token-not-a-real-secret",
  "accessTokenExpiresAt": "2026-10-19T17:00:00+08:00",
  "refreshToken": "example-refresh-token-not-a-real-secret",
  "refreshTokenExpiresAt": "2026-11-18T16:00:00+08:00",
  "user": {"id": "user-example-001", "displayName": "示例用户"},
  "activationId": null
}
```

账号尚未授权也可登录，不能在登录阶段返回 LICENSE_REQUIRED 阻断兑换。若后端确认当前设备已有合法绑定，可直接返回 activationId；否则先返回未绑定会话，允许访问会话、能力、兑换和激活接口，模型调用返回 403 DEVICE_NOT_ACTIVATED。账号无有效 License 则优先返回相应授权错误。

/session 示例：

```json
{
  "user": {"id": "user-example-001", "displayName": "示例用户"},
  "license": {
    "status": "active",
    "expiresAt": null,
    "features": ["chat", "image", "voice-recognize", "voice-synthesize", "embeddings"],
    "maxDevices": 1,
    "activatedDevices": 1
  },
  "deviceAuthorization": {
    "activationId": "activation-example-001",
    "status": "active",
    "refreshAfter": "2026-10-19T16:30:00+08:00",
    "offlineUntil": null
  },
  "serverTime": "2026-10-19T16:00:00+08:00"
}
```

license.status 枚举 active/inactive/expired/revoked；deviceAuthorization.status 枚举 active/unbound/revoked。未绑定时 activationId=null。未授权时 license.status=inactive、features=[]；永久授权 expiresAt=null。本期默认远程模型调用必须联网，不引入签名离线许可证；offlineUntil=null。离线读取本地业务内容的产品规则不等同于拥有云端调用权限。若未来要求离线锁定整个应用，另行扩展。

### 10.2 激活、刷新与竞态

1. 激活名额检查和绑定在后端事务中完成，同机重复激活幂等，不重复占用名额。机器摘要必须与当前登录会话登记的一致；凭据本身由后端签发，不把机器码当访问凭据。
2. activate 成功返回绑定当前 activationId 的新 token 对；客户端原子替换并安全保存，后续模型、停止、查询、下载均使用当前会话。旧未绑定 token 不能据此获得模型权限。
3. refresh 轮换刷新凭据并保留设备绑定；服务端验证账号、会话、设备状态，不通过 token 刷新重新激活被撤销设备。客户端单飞刷新，避免并发使用轮换 token 导致误判。重用已撤销刷新凭据返回 401 SESSION_REVOKED，重新登录，不无限重试。
4. 访问凭据在请求开始时有效即可完成一次受限时长调用；不要因自然到期截断已接受的 SSE。账号禁用、主动退出或设备撤销按后端策略尽快取消关联在途调用，并返回明确错误。
5. 仅业务请求的 401 AUTH_EXPIRED 进入刷新流程；登录的 INVALID_CREDENTIALS 直接显示登录失败，刷新接口的 SESSION_REVOKED 重新登录，不能统一对所有 401 递归刷新。普通幂等读取可用新 token 重试一次。付费请求使用原 requestId 查结果，必要时以同 ID 同参数重传，不能为自动重试换 ID。403 License/设备问题不通过刷新循环解决。
6. 无有效授权时仍允许登录、会话查询、能力查询、兑换及合法激活。为避免持续付费或丢失已付费结果，授权自然过期后，已归属本用户的停止/取消、状态查询和临时结果下载不要求再次购买；仍校验有效账号身份与任务归属。账号/设备被主动撤销时由服务端取消关联任务，禁止继续创建调用。
7. 设备指纹原始序列号不必上传；算法、产品域隔离和无效值过滤由客户端与后端约定。设备损坏或换主板由后端运营手段重置，本期不要求新增设备管理前端。

第一期建议 access token 有效期 1 小时、refresh token 30 天，实际以响应时间字段为准。应用凭据仅在桌面主进程/本地受保护服务中保存，供应商密钥不下发。不同账号的本地业务数据和媒体缓存分别隔离；已有业务数据的归属处理由客户端负责，不增加远程业务 CRUD。

## 11. 不要求远程后端开发的内容

- 不需要对话历史、智能体、知识库、头像设置的云端 CRUD。
- 不需要给“普通聊天”“智能体聊天”“翻译助手”“调试”“摘要”各做一个生成接口。
- 不需要后端接收本地会话 ID 并读取本地文件。
- 不需要原始向量导入导出、文档解析、相似度检索或完整知识库数据库。
- 不需要录音、播放器、暂停/继续、本地头像同步接口。
- 不需要本地模型、实时语音通话、动态数字人或视频口型。

管理后台模型设置接口由管理前端单独对接，不向普通客户端开放；本文件聚焦客户端业务调用。

## 12. 联调验收

最少验收：

1. 同一个对话接口跑通普通聊天、智能体、未保存调试和非流式摘要。
2. SSE 首段及时到达、正常终止和截断错误可区分，停止在摘要期间和回答期间均有效。
3. 断线/重复请求不重复扣费、不重复添加本地消息，查询状态可恢复结果。
4. WAV 录音直接识别；点击朗读一次即播放，逐段顺序、暂停/继续/停止与缓存正常。
5. 文字/参考图生成可查询和取消；生成文件落到本地后可保存、重启恢复及分别同步形象和位置。
6. 文档/查询向量正确排序、1024 维、重启复用；远程空间切换不混用旧索引。
7. 账号停用、License 过期、设备超限、限流、审核失败、服务故障分别返回稳定错误；用户不能读取他人的请求/任务/音频/图片。
8. 普通客户端不包含供应商密钥和管理入口；接口不能由用户覆盖模型或任意目标地址。

后端评审时需确认：实际路径、认证策略、状态与结果保留时间、是否支持生成组取消、流式代理配置、向量空间版本策略、各接口实际限额与超时，并提供 OpenAPI 和联调环境。

部署边界：桌面客户端通过受保护的本地服务发起远程请求，服务端无需开放任意 CORS Origin。管理 Web 前端的 CORS、Cookie 和 CSRF 规则按管理域名单独配置。

## 13. 开发前待双方确认

以下内容采用本文默认值；如有调整，需要同步更新文档与联调示例：

1. 接口前缀、账号来源、登录方式，以及设备指纹算法。本文规定指纹为 64 位十六进制摘要，但原始硬件信息、哈希算法、产品域和容错规则需双方确认；该问题未确定前不能完成设备绑定联调。
2. 对话供应商地址与有效模型标识。供应商规范列出目前配置的 `https://api.deepseek.com` + `deepseek-flash`，该组合须通过测试账号验证后才能作为生产默认值。
3. 请求恢复、结果与媒体保留期是否采用至少终态后 24 小时；各接口是否接受本文大小限制和超时。
4. SSE 代理是否关闭缓冲；是否实现生成组取消及未知 generationId 的短期取消标记。
5. 模型、音色和向量空间版本变更规则，以及稳定错误码。
6. 管理后台的配置/权限 API 另行定义，由超级管理员使用；不混入普通客户端接口。

服务端交付物：与本文一致的 OpenAPI、可用测试环境、测试账号与 License、已配置的模型服务，以及成功/失败/SSE/取消/断线恢复的联调样例。文档和模拟示例不代替真实服务验收。
