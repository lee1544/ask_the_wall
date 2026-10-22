# 模型供应商接入规范

修订日期：2026-10-22。面向服务端开发人员。本文规定服务端调用模型供应商时使用的模型、地址、请求体、响应解析及媒体处理规则；无需阅读客户端代码。

客户端调用服务端的字段、状态码、鉴权和幂等规则见 [客户端服务 API 实现规范](backend-api-minimal-contract.md)。**本文 JSON 是“服务端 → 供应商”的请求，不是客户端请求。** 两层令牌和字段不同。

## 1. 模型与连接配置

| 服务端能力 | 模型标识 | 配置要求 |
| --- | --- | --- |
| 对话、智能体、翻译、调试、知识库问答、摘要 | `deepseek-flash` | 当前配置基线；地址 `https://api.deepseek.com`；有效性待联调确认 |
| 语音识别 ASR | `qwen3-asr-flash` | 百炼 |
| 语音合成 TTS | `qwen3-tts-flash` | 百炼，默认音色 `Cherry` |
| 静态头像生成 | `wan2.7-image` | 百炼，单次一张、2K |
| 文本向量 | `text-embedding-v4` | 百炼，1024 维 dense |

对话模型启用联网时，还会调用 DeepSeek 原生 Search/Fetch 工具，见 3.1 节。

百炼初始配置采用北京地域、业务空间 ID 为空，公共域名 `https://dashscope.aliyuncs.com`。四项百炼能力可共用该地域的一个服务端密钥；对话连接使用独立密钥。所有密钥由服务端保存和管理，不向客户端返回。

**待确认项：** 对话模型与地址来自现有配置记录，尚不能作为其可用性的证明。服务端在联调环境验证该组合；如供应商不支持此模型标识，需双方确认实际可用模型，不能静默替换。本期不要求实现 `gpt-4.1-mini` 备用路由或自动切换供应商。

模型与音色由超级管理员配置，普通业务请求不能覆盖；启用状态通过 /capabilities 返回。服务端须实际完成配置与权限验证后再开放对应能力。

## 2. 百炼连接与供应商路径

默认使用北京公共域名。若管理端支持其他地域或业务空间，按下表选择 origin，并验证该账号对应地域的模型权限。

| 地域 | 不填写业务空间 ID | 填写业务空间 ID，例如 `WORKSPACE_ID` |
| --- | --- | --- |
| 北京 `beijing` | `https://dashscope.aliyuncs.com` | `https://WORKSPACE_ID.cn-beijing.maas.aliyuncs.com` |
| 新加坡 `singapore` | `https://dashscope-intl.aliyuncs.com` | `https://WORKSPACE_ID.ap-southeast-1.maas.aliyuncs.com` |

业务空间 ID 最多 64 字符，允许字母、数字和连字符，首字符须为字母或数字。地域与密钥必须配套。以下以 `BAILIAN_ORIGIN` 表示选中的 HTTPS origin：

| 能力 | POST 路径 |
| --- | --- |
| ASR | `BAILIAN_ORIGIN/compatible-mode/v1/chat/completions` |
| TTS、头像 | `BAILIAN_ORIGIN/api/v1/services/aigc/multimodal-generation/generation` |
| 向量 | `BAILIAN_ORIGIN/api/v1/services/embeddings/text-embedding/text-embedding` |

供应商 POST 均发送 `Content-Type: application/json` 和 `Authorization: Bearer <服务端供应商密钥>`，禁止自动跟随重定向。后端对客户端的应用登录 Token 与这些供应商密钥完全独立。媒体下载不携带这两类凭据。

## 3. 对话调用

对话基线 URL 为 `https://api.deepseek.com/chat/completions`。通用规则是去掉配置 baseUrl 的末尾斜杠，再追加 `/chat/completions`；配置已有 `/v1` 时保留，不能强行补上或删掉。

普通请求示例（messages 内容为合成示例）：

```json
{
  "model": "deepseek-flash",
  "stream": true,
  "messages": [
    {"role": "system", "content": "你是一个中文助手。"},
    {"role": "user", "content": "介绍一下你自己。"}
  ]
}
```

普通问答、智能体、翻译、调试、知识库问答和摘要使用同一套服务端模型策略；翻译与智能体的区别由 messages 中的 system 内容表达，不需要单独的翻译模型或接口。普通用户不能指定模型。

客户端已将系统提示词、知识片段与历史整理为 messages。服务端按顺序传给模型，不重复检索、摘要或拼接提示词，也不按业务 ID 查询知识库。

`temperature`、`top_p`、`seed` 与惩罚项不额外设置，使用供应商默认值。输出预算按 /capabilities 公布的策略执行：基准 maxOutputTokens 为 8192；普通回答未指定 max_tokens 时向供应商发送策略上限，指定时不得超过上限。摘要使用同一模型，stream=false，max_tokens 默认 768，且不超过策略上限。上方 JSON 只展示模型和消息映射，实际请求应补入经限额校验的 max_tokens。

非流式读取 `choices[0].message.content`、可选 `usage`；`choices[0].finish_reason === "length"` 表示截断。流式读取 `data:` SSE 帧中的 `choices[0].delta.content`，终帧处理 finish_reason 和 usage，以 `[DONE]` 结束。空文本、供应商错误帧及异常中断不能包装成成功。上游超时基准 300 秒，回答累计最多 200,000 字符。

服务端按客户端 API 规范返回 choices 结构；不额外包装业务会话对象或自定义页面事件。

### 3.1 联网搜索与网页抓取

启用联网能力时，answer 对话仍走 DeepSeek Chat Completions，但请求体额外携带 `tools`，并设置 `tool_choice: "auto"`，由模型决定是否调用工具。仅在 purpose=answer、服务端配置了 `client.deepseek.api-key` 且 `client.web-search.enabled=true` 时发送工具定义；摘要请求不发送工具。工具调用和搜索结果不向客户端暴露。

工具定义：

| 工具 | 参数 | 用途 |
| --- | --- | --- |
| `web_search` | `query` 必填，最多 128 字符；`count` 可选，1–5；`ori_question` 可选，最多 128 字符 | 搜索实时信息，支持常见搜索操作符 |
| `web_fetch` | `url` 必填，HTTP/HTTPS、最多 8192 字符、禁止用户名密码 | 抓取指定网页的 Markdown 正文 |

搜索与抓取使用 DeepSeek 原生工具接口。将 `DEEPSEEK_BASE_URL` 去掉末尾斜杠；若已以 `/v1` 结尾，则分别追加 `/tools/search`、`/tools/fetch`，否则追加 `/v1/tools/search`、`/v1/tools/fetch`。请求头使用对话同一服务端密钥的 `Authorization: Bearer <key>`，超时基准为连接 5 秒、读取 30 秒。

搜索请求示例：

```json
{"query": "2026 年 10 月 AI 新闻", "count": 5, "ori_question": "最近有哪些 AI 新闻？"}
```

搜索响应读取 `results[]`，每项可含 `title/url/snippet/summary/date/site_name/rerank_score/authority`。只接受 HTTP/HTTPS 结果；摘要优先取 `summary`，缺失时取 `snippet`，最多 800 字符；标题最多 200 字符。抓取响应读取 `title/content`，正文按 Markdown 保留换行，最多 8000 字符。

工具循环最多 4 轮，每轮最多执行 3 个工具调用；超出数量或轮次的调用以工具错误 JSON 返回，不再访问搜索接口。搜索/抓取失败也以工具错误 JSON 作为 `role=tool` 消息返回，由模型基于已有信息继续回答，不能把工具失败直接包装成成功答案。发送给供应商的历史消息中，assistant 的 `tool_calls` 与对应 `tool_call_id` 必须原样保留。

## 4. ASR：qwen3-asr-flash

对应远程 `POST /api/v1/client/voice/recognize`。客户端给后端的是原始 WAV，后端给百炼时改为 Base64 JSON：

```json
{
  "model": "qwen3-asr-flash",
  "messages": [{
    "role": "user",
    "content": [{
      "type": "input_audio",
      "input_audio": {"data": "data:audio/wav;base64,BASE64_WAV_BYTES"}
    }]
  }],
  "stream": false,
  "asr_options": {"enable_itn": true}
}
```

`BASE64_WAV_BYTES` 为说明性占位内容，必须换成真实 WAV 字节的 Base64。不额外发送 format、sample_rate 或强制语言字段；保留 `enable_itn: true`。

输入：RIFF/WAVE，PCM 16 位，小端，16000 Hz，单声道，0.1–60 秒，最多 1,920,044 字节；静音检查采用 RMS 阈值 0.001。不得把浏览器原始 WebM/Opus 直接标成 WAV。识别结果取 `choices[0].message.content`，trim 后非空且最多 16000 字符；远程返回 `{text, requestId}`，不自动发起对话。语音任务超时基准 90 秒，原录音不落盘。

## 5. TTS：qwen3-tts-flash

对应远程 `POST /api/v1/client/voice/synthesize`。服务端先按下述规则把整条 Markdown 回答分段，只合成指定 segmentIndex 对应的文本：

```json
{
  "model": "qwen3-tts-flash",
  "input": {
    "text": "你好，我是你的中文助手。",
    "voice": "Cherry",
    "language_type": "Auto"
  }
}
```

允许的音色为 `Cherry`、`Serena`、`Ethan`，默认 Cherry；由管理端设置，普通请求不能覆盖。不额外设置语速、音量、采样率、输出格式或流式参数。

服务端会把单个对外片段再拆成默认不超过 200 字符的供应商请求并合并 WAV，避免长片段在供应商侧长时间无响应；对外分段结果、segmentIndex 和 configVersion 不变。

### 分段规则必须一致

分段版本为 `speech-segments-v1`。字符长度按 UTF-16 单位计算，逐字符遍历时不拆开 Unicode 代理对。算法顺序如下：

1. 输入整条 Markdown，最多 32000 字符；用 Markdown lexer 解析，移除代码块、行内代码、HTML、图片、分隔线。表格依次取表头与各行单元格，用中文逗号连接；列表用换行连接；直接显示 URL/mailto 的链接跳过，文字链接保留标签文字。
2. 删除裸 HTTP/HTTPS URL，解码 `&amp;`、`&lt;`、`&gt;`、`&quot;`、`&#39;`，连续空格/制表符合并，连续三次以上换行合并为两次，trim。清洗后为空则返回无可朗读内容错误。
3. 用 `/[^。！？!?\n]+[。！？!?\n]*|[。！？!?\n]+/g` 切句。英文句点不属于该正则的断句符。
4. 逐 Unicode 字符累计；首段上限 120 个 UTF-16 单位，其余段上限 500，追加字符会越界时先输出已有片段；每句结束后，首段已达 40 或后续段已达 350 就输出；最终输出非空余段。不能简化成统一每 500 字切割。

服务端直接按上述算法实现，并通过下表样例验证。分段规则、模型或音色变化均需要更新远程 configVersion。远程参数的 text 是整条原始 Markdown，不能把 segmentIndex 对应的单段传入后再二次分段。

### 分段验收样例

以下 `重复 N 次` 表示测试构造方式，不是实际传给接口的文字；换行用 `\n` 表示。

| 输入 | 预期片段数组 |
| --- | --- |
| `你好！` | `["你好！"]` |
| `**你好**，世界！` | `["你好，世界！"]` |
| `正文。\n\n` 后接代码块 | `["正文。"]` |
| 只有代码块或图片 | 422 NO_READABLE_TEXT |
| 字符“甲”重复 121 次 | 首段 120 个“甲”，第二段 1 个“甲” |
| 字符“甲”重复 621 次 | 三段，长度依次 120、500、1 |
| 字符“甲”重复 40 次，接 `。乙。` | 两段：40 个“甲”加 `。`；`乙。` |

### 下载与播放兼容

取百炼响应 `output.audio.url`，不是 choices。只接受官方 OSS `*.oss-*.aliyuncs.com` 主机的 HTTP/HTTPS 地址；HTTP 仅升级为 HTTPS，保留原路径及签名查询参数；禁止用户名、密码、显式端口及重定向。音频下载不带供应商 Authorization。后端将验证后的音频发布为本系统媒体资源，不把 OSS 临时签名地址直接给播放器。

结果最多 16 MiB，须为完整 RIFF WAV，PCM 16 位，1/2 声道，采样率允许 16000/22050/24000/44100/48000。需要兼容百炼 WAV 的已知占位长度：44 字节标准头、fmt 长度 16、data 标签在偏移 36，RIFF 长度 `0x7fffffbf` 与 data 长度 `0x7fffff9b` 同时出现，PCM 与 blockAlign 校验通过且数据对齐时，才分别改写为 `总字节数 - 8` 与 `总字节数 - 44`。不能宽泛忽略损坏头。

任务超时基准为 90 秒，包含合成与下载。缓存按用户/权限隔离，键至少包含配置版本、分段版本和实际片段文本。服务端缓存有效期与恢复规则按客户端 API 规范，返回 url/segmentIndex/segmentCount/cached/configVersion/expiresAt。客户端自行播放与续播，服务端不实现播放器。

## 6. 头像：wan2.7-image

对应 `POST /api/v1/client/avatar/generate`。**百炼调用采用同步生成，异步任务由服务端自行管理**；不发送 X-DashScope-Async，也不依赖供应商 task_id 轮询。

纯文字示例：

```json
{
  "model": "wan2.7-image",
  "input": {"messages": [{
    "role": "user",
    "content": [{"text": "生成用于页面头像展示的静态人物肖像，头肩构图，主体居中，背景简洁，不添加文字。\n一位穿蓝色衬衫的年轻女性，写实风格。"}]
  }]},
  "parameters": {"size": "2K", "n": 1}
}
```

参考图示例：

```json
{
  "model": "wan2.7-image",
  "input": {"messages": [{
    "role": "user",
    "content": [
      {"image": "data:image/jpeg;base64,BASE64_JPEG_BYTES"},
      {"text": "生成用于页面头像展示的静态人物肖像，头肩构图，主体居中，背景简洁，不添加文字。根据参考图片保留人物主要特征。\n保留人物特征，换成简洁白色背景。"}
    ]
  }]},
  "parameters": {"size": "2K", "n": 1}
}
```

参考图排在 text 前。服务端将 prompt 去首尾空白，要求非空且最多 3000 字符，再添加上例固定前缀一次；有参考图时加入“根据参考图片保留人物主要特征。”。客户端提供已铺白底的 JPEG Data URL，服务端校验真实 JPEG、宽高各 240–8000、宽高比不超过 8、解码后不超过 20 MiB。无需公网图床，不接受客户端路径或任意下载 URL。

从 `output.choices[*].message.content[*].image` 中取第一张结果。JSON 响应最多 1 MiB；下载仅接受 HTTPS 官方 OSS 主机、无凭据/显式端口，禁止重定向。GET 不带密钥；校验 image/png MIME、PNG 签名、实际可解码、最多 20 MiB、单边不超过 8192、总像素不超过 32×1024×1024。验证后原子保存，不用云端临时地址作为永久头像。

总超时基准 180 秒，包含生成与下载。服务端任务状态为 queued/running/downloading/complete/error/cancelled，无虚构百分比。完成后返回受保护媒体地址，客户端负责下载保存。取消停止本地等待和下载，不能承诺撤销云端计费。创建任务的 HTTP 结束不应使后台生成自动终止。

## 7. 向量：text-embedding-v4，固定 1024 维

对应远程 `POST /api/v1/client/embeddings`。使用百炼原生接口，不是兼容模式 `/embeddings`。示例：

```json
{
  "model": "text-embedding-v4",
  "input": {"texts": ["本产品支持知识库问答。", "系统支持保存和恢复历史对话。"]},
  "parameters": {
    "dimension": 1024,
    "text_type": "document",
    "output_type": "dense"
  }
}
```

查询问题使用相同模型和维度，仅将 `text_type` 改成 `query`。注意百炼原生参数是单数 **dimension**，远程返回给客户端的是 **dimensions**。不启用 sparse，不增加重排模型，也没有另一套 query 模型。

每次 1–10 段、每段非空且最多 4000 个 UTF-16 单位，超时每批 60 秒，JSON 结果最多 2 MiB。客户端提交已经切好的文档片段，服务端不再次切块。问题按 query，文档按 document 编码。

读取 `output.embeddings`，每项包含 text_index 和 embedding。必须校验数量与输入相同、text_index 不重复且覆盖 0..N-1，再按索引重排；每个向量恰好 1024 维、全为有限数且范数非零。不要假定供应商总按输入顺序返回，不要做无约定的截断、补零或精度压缩。

远程结果为 `{vectors, dimensions: 1024, spaceId, requestId}`，usage 可选，完整 1024 维示例见配套 JSON。知识库原文、向量索引和相似度检索由客户端管理，不属于本接口职责。

服务端维护不透明的 spaceId：模型、维度或语义处理参数变化时更新；单纯轮换密钥保持不变。本期固定 1024 维，不能只改 capabilities 就发布其他维度。调用时 expectedSpaceId 不一致须返回 409 INDEX_SPACE_CHANGED，不能静默换空间。

## 8. 错误、超时与实施验收

HTTP 成功也要检查供应商 JSON 的 code 字段。供应商密钥失效向客户端返回安全的供应商连接错误，不要直接返回应用级 401 触发登录刷新；应用身份过期与供应商鉴权失败是两回事。余额不足、限流、审核拒绝、权限/地域错误、下载失败与超时须区分。不得透传密钥、完整内部配置或带签名下载链接到日志/错误。

| 供应商操作 | 超时基准 | 处理要求 |
| --- | --- | --- |
| 对话（含摘要单次调用） | 120 秒 | SSE 反向代理关闭缓冲，取消按 generationId 覆盖摘要与回答 |
| ASR | 90 秒 | 音频上传、Base64 后请求限制也需覆盖 |
| TTS | 90 秒 | 包含结果下载，缓存命中不重复付费 |
| 头像 | 180 秒 | 包含结果下载，应用后台任务继续运行 |
| 向量 | 每批 60 秒 | 10 条为一批，不因网络故障自动重复付费 |

联网工具自身按连接 5 秒、读取 30 秒控制；工具轮次会增加对话上游调用次数，整体仍受服务端生成的流超时约束。并发限制按用户、授权和服务端容量制定；不同用户的任务应隔离。超时不是模型性能承诺，反向代理须允许正常请求完成。

交接验收至少覆盖：同样 messages 的普通/翻译/调试/知识库回答；摘要 768 输出约束；ASR 中文数字识别与静音；三种音色、长 Markdown 分段连续播放、HTTP OSS 地址与 WAV 占位长度；文字/参考图头像和取消；document/query 两类 1024 维向量及顺序恢复。所有付费操作同时遵守主契约 requestId 幂等与结果恢复，失败不自动重试。

## 9. 服务端交付检查

- 配置已保存于服务端，普通用户不能读取密钥、改模型或指定目标 URL。
- 五类模型能力遵守客户端 API 规范，尤其是 requestId、generationId、版本校验及 24 小时恢复期。
- 返回给客户端的是业务响应和受保护的媒体地址，不是供应商原始响应。
- 分段与媒体验证可通过独立用例验收，不依赖客户端源码。
- 对话模型/地址组合及地域权限在真实测试环境验证；管理配置 API 另行对接。
