# LDD Agent

一个可扩展的多 Skill 对话应用。当前版本包含模型自动路由、手动 Skill 选择、MySQL 会话历史、Redis 路由缓存和响应式聊天页面，并为 Agent 能力保留了独立模式。

## 启动配置

MySQL 和 Redis 连接位于 `src/main/resources/application.properties`。应用启动时会通过 `schema.sql` 自动创建会话表。本地密码可以放在被 Git 忽略的 `src/main/resources/application-local.properties`，部署环境建议使用 `DB_URL`、`DB_USERNAME` 和 `DB_PASSWORD` 环境变量。

模型使用 OpenAI-compatible Chat Completions 接口，通过环境变量配置：

```text
MODEL_BASE_URL=https://api.openai.com/v1
MODEL_API_KEY=your-api-key
MODEL_NAME=your-model-name
MODEL_CHAT_PATH=/chat/completions
```

其中 `MODEL_NAME` 必填；不需要鉴权的本地模型可以不设置 `MODEL_API_KEY`。

启动后访问 `http://localhost:8080/`。

## Cursor Cloud Agent

页面的 `Agent` 模式调用 Cursor Cloud Agents API v1，在 Cursor 云端执行研发任务。服务端使用 `CURSOR_API_KEY`，不会把密钥返回浏览器。截图中的 Cursor User API Key 应通过环境变量传入：

```text
CURSOR_API_KEY=crsr_...
CURSOR_REPO_URL=https://github.com/your-org/your-repo
CURSOR_STARTING_REF=main
CURSOR_AUTO_CREATE_PR=true
CURSOR_MODEL_ID=                 # 可选，例如 composer-2
```

也可以直接在页面 Agent 模式中填写仓库地址、起始分支和是否自动创建 PR。接口使用 Cursor 官方 v1 规范：创建任务 `POST https://api.cursor.com/v1/agents`，服务端以 Basic Auth 传入 API Key，随后轮询 `GET /v1/agents/{agentId}/runs/{runId}` 展示 `FINISHED`、`ERROR`、`CANCELLED` 或 `EXPIRED` 结果。

请不要把 API Key 写入源码、properties 文件或前端；如果密钥曾经被提交或粘贴到代码中，应先在 Cursor 控制台撤销并重新生成。

## 新增 Skill

在 `src/main/resources/skills/` 新增 JSON 文件即可，无需修改路由代码：

```json
{
  "id": "order-assistant",
  "name": "订单助手",
  "description": "处理订单查询、状态解释和异常排查",
  "systemPrompt": "你是订单领域助手。回答必须遵循订单状态流转规则。",
  "examples": ["订单为什么没有出库"],
  "enabled": true
}
```

`description` 和 `examples` 用于模型路由，`systemPrompt` 用于选中后的正式回答。

## Agent 扩展点

接口中的 `CapabilityMode.AGENT`、页面模式切换和独立异常码已经保留。接入 Agent 时建议新增 `AgentRegistry`、`AgentRouter` 和执行器实现，并让 `ChatService` 根据 `CapabilityMode` 分发；Skill 注册和执行路径不需要改动。
