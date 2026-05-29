# 商城智能客服 —— Spring AI Alibaba Graph + DeepSeek 完整实战

> 涵盖：多场景意图识别 → Graph 工作流 → 可配置外部 API → 自研 AES Token 登录 → PostgreSQL 对话持久化 → 历史会话管理 → B3 链路追踪

---

## 功能概览

| 模块 | 功能 |
|------|------|
| **用户客服** | 同步/流式（SSE）聊天；实时进度气泡；历史会话列表；切换会话查看完整记录 |
| **账号系统** | 注册/登录（自研 AES-256 Token）；头像下拉菜单退出；自动续期/401 跳转 |
| **Admin 配置** | Postman 风格接口配置；JSON 美化；响应面板可拖拽调整高度；一键测试 |
| **链路追踪** | Micrometer Tracing（B3 格式）；每个响应携带 `X-B3-TraceId`；服务间自动传播；日志自动关联 |
| **局域网访问** | Vite `host: 0.0.0.0`，同局域网设备可通过 IP 访问前端 |

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 3.5.0 | Web 框架 |
| Spring AI Alibaba | 1.1.2.0 | Graph 工作流引擎 |
| Spring AI | 1.1.2 | AI 抽象层 |
| DeepSeek | deepseek-chat | 大语言模型 |
| MyBatis | 3.0.4 | SQL 映射持久化框架 |
| PostgreSQL | 15+ | 关系型数据库 |
| Spring Security | 6.x | 安全框架（无状态 Stateless）|
| **自研 AES Token** | JDK 内置 `javax.crypto` | AES-256/CBC 加密令牌，替代 JWT 第三方库 |
| **Micrometer Tracing** | Spring Boot 3.x 内置 | B3 链路追踪，替代 Spring Cloud Sleuth |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| **Vue 2** | 2.7.14 | 前端框架（Options API）|
| **Element UI** | 2.15.14 | Vue 2 专用 UI 组件库 |
| Vue Router | 3.6.5 | 路由管理 |
| Vite | 5.x | 前端构建工具 |
| @vitejs/plugin-vue2 | 2.3.x | Vite 的 Vue 2 编译插件 |
| Axios | 1.x | HTTP 客户端 |

---

## 系统架构

```
用户浏览器
   │
   ▼ (AES Token)
[Vue 2 前端 :3000]
   │  /api/auth/**        → 登录 / 注册 / 当前用户信息
   │  /api/chat           → 同步聊天（响应体含 traceId）
   │  /api/chat/stream    → 流式聊天（SSE，响应头含 X-B3-TraceId）
   │  /api/chat/sessions  → 历史会话列表
   │  /api/chat/history/* → 会话聊天记录
   │  /api/mock-config/** → Admin 接口配置管理
   ▼
[Spring Boot 后端 :8080]
   │
   ├── Filter 链（按执行顺序）
   │      ├── ServerHttpObservationFilter    ← 创建 Span，MDC 写入 traceId/spanId
   │      ├── TraceIdResponseFilter          ← 所有响应注入 X-B3-TraceId 等头
   │      └── Spring Security FilterChain   ← AES Token 鉴权 + CORS + Async 放行
   │
   ├── CustomerServiceController
   │      └── Spring AI Graph
   │              ├── IntentParserNode       ← LLM 意图识别
   │              ├── OrderStatusNode        ← 查订单状态
   │              ├── LogisticsQueryNode     ← 查物流
   │              ├── OrderReviewNode        ← 提交评价
   │              ├── ResponseFormatterNode  ← LLM 格式化回复
   │              └── GeneralAnswerNode      ← 通用问答 LLM
   │
   ├── ChatHistoryAdvisor  → @Async 异步持久化对话记录
   │
   └── MockApiConfigService → 从 DB 读取可配置外部 API
          └── ConfigurableMallDataService（@Primary）
                 └── tracingRestTemplate（B3 头传播）→ 外部商城 API
                 └── 降级 → MockMallDataService（内置 Mock）
                                       ↕
                              [PostgreSQL :5432]
                              ├── app_user        （用户账号）
                              ├── chat_history    （对话记录）
                              └── mock_api_config （接口配置）
```

---

## 链路追踪设计

### 概述

本项目使用 **Micrometer Tracing + Brave** 实现 B3 格式链路追踪（Spring Boot 3.x 对应 Spring Cloud Sleuth 的官方替代方案）。

### TraceId 携带范围

| 场景 | 携带方式 |
|------|---------|
| 所有 HTTP 响应（包括错误响应）| 响应头 `X-B3-TraceId`、`X-B3-SpanId`、`X-Trace-Id` |
| 同步聊天接口响应体 | `ChatResponse.traceId` 字段 |
| 服务调用外部商城 API | 请求头 `X-B3-TraceId`、`X-B3-ParentSpanId`、`X-B3-Sampled` |
| 应用日志 | 每行日志自动携带 `traceId` 和 `spanId` |

### 过滤器执行顺序

```
请求入站
  │
  ▼  order = HIGHEST_PRECEDENCE + 1 (-2147483647)
ServerHttpObservationFilter     ← 创建 HTTP Span，MDC 写入 traceId/spanId
  │
  ▼  order = HIGHEST_PRECEDENCE + 10 (-2147483638)
TraceIdResponseFilter           ← 读取 Span → 写响应头 X-B3-TraceId 等
  │  （在响应体写出前完成，SSE 流式也能携带）
  │
  ▼  order = -100
Spring Security FilterChain     ← AES Token 鉴权 / CORS / Async 放行
  │
  ▼
Controller / SSE Stream
```

### 日志格式

```
10:30:15.123 [http-nio-8080-exec-1] INFO  [trace=abc123def456,span=789012] c.e.m.c.CustomerServiceController - 收到对话...
10:30:15.456 [http-nio-8080-exec-1] INFO  [trace=abc123def456,span=789012] c.e.m.s.ConfigurableMallDataService - 外部API响应状态: 200 OK
```

### 前端读取 TraceId

```javascript
// 方式一：从同步聊天响应体读取
const { traceId } = res.data

// 方式二：从任意响应头读取（需 CORS 已暴露该头）
const traceId = response.headers['x-b3-traceid']
```

---

## 认证令牌设计

### 自研 AES-256/CBC Token

本项目使用 JDK 内置 `javax.crypto` 实现 AES-256/CBC 加密令牌，**不依赖任何第三方 JWT 库**。

| 特性 | 说明 |
|------|------|
| 算法 | AES-256/CBC + PKCS5Padding |
| 密钥派生 | SHA-256(secret) 得到 256 位密钥 |
| IV | 每次生成随机 16 字节 IV，与密文一起 Base64 存储 |
| Payload | `{"u":userId,"n":username,"r":role,"e":expireMs}` |
| 有效期 | 24 小时（可配置 `token.expiration`）|

**Token 格式：** `Base64(IV) + "." + Base64(ciphertext)`

---

## Graph 工作流

```
用户输入
   │
   ▼
[intent_parser]  ← DeepSeek 识别场景、提取订单号/评分等实体
   │
   ├─ logistics_query  ──► [logistics_query_node] ──► [response_formatter] ──► END
   ├─ order_status     ──► [order_status_node]    ──► [response_formatter] ──► END
   ├─ order_review     ──► [order_review_node]    ──► [response_formatter] ──► END
   └─ general          ──► [general_answer_node]                            ──► END
```

---

## 快速启动

### 前置依赖

- **JDK 17**（`java -version` 确认）
- **Maven 3.8+**（或使用项目内 `mvnw`）
- **PostgreSQL 15+**（本地运行，默认端口 5432）
- **Node.js 18+**（前端）
- **DeepSeek API Key**（[申请地址](https://platform.deepseek.com/)）

### 1. 准备数据库

```sql
-- 使用默认 postgres 库，或手动建库：
CREATE DATABASE mall_cs;
```

> 表结构和初始数据由 Spring Boot 启动时自动执行  
> `src/main/resources/sql/schema.sql` 和 `src/main/resources/sql/data.sql`

### 2. 修改后端配置

编辑 `src/main/resources/application.yml`，填入实际信息：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres   # 数据库地址
    username: postgres                               # 数据库用户名
    password: 123456                                 # 数据库密码
  ai:
    deepseek:
      api-key: sk-your-api-key-here                  # DeepSeek API Key

token:
  secret: your-secret-key-at-least-32-chars-long     # AES 加密密钥（≥32 字符）
  expiration: 86400000                               # Token 有效期（毫秒，默认 24h）

management:
  tracing:
    sampling:
      probability: 1.0    # 链路追踪采样率（生产环境建议 0.1）
```

### 3. 启动后端

```powershell
cd my-project/codeAIGraph

# 若系统默认 JDK 版本 < 17，先指定 Java 17
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

mvn spring-boot:run
```

后端启动成功标志：`Started MallCustomerServiceApplication`，监听 `http://localhost:8080`

### 4. 启动前端

```powershell
cd my-project/mall-frontend
npm install --legacy-peer-deps   # 首次运行（Vue 2 peer deps 需要此参数）
npm run dev
```

| 访问方式 | 地址 |
|----------|------|
| 本机访问 | http://localhost:3000 |
| 局域网访问 | http://\<本机IP\>:3000 |

> 本机 IP 查询：`(Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notmatch 'Loopback' }).IPAddress`

---

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| user1 | user123 | 普通用户 |

> 账号由 `DataInitializer` 在应用启动时自动创建（密码 BCrypt 加密）

---

## 接口说明

### 认证接口（无需 Token）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 AES Token |
| POST | `/api/auth/register` | 注册新用户 |
| GET | `/api/auth/me` | 获取当前用户信息（需 Token）|

**登录示例：**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"user123"}'
```

返回：
```json
{
  "token": "Base64IV.Base64Ciphertext",
  "userId": 2,
  "username": "user1",
  "nickname": "用户1",
  "role": "USER"
}
```

### 聊天接口（需携带 Token）

所有请求需加 Header：`Authorization: Bearer <token>`

所有响应头自动携带：`X-B3-TraceId`、`X-B3-SpanId`、`X-B3-Sampled`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 同步聊天，响应体含 `traceId` 字段 |
| POST | `/api/chat/stream` | 流式聊天（SSE），实时推送进度+回复 |
| GET | `/api/chat/sessions` | 获取当前用户历史会话列表 |
| GET | `/api/chat/history/{sessionId}` | 获取指定会话的完整聊天记录 |

**同步聊天响应示例：**
```json
{
  "sessionId": "uuid-xxx",
  "answer": "您好！您的 ORD001 订单已完成...",
  "sceneType": "order_status",
  "orderNo": "ORD001",
  "success": true,
  "errorMessage": null,
  "traceId": "abc123def456789"
}
```

**流式接口 SSE 事件格式：**
```
data: [PROGRESS] 🔍 正在分析您的问题...   ← 中间进度
data: [PROGRESS] ✍️ 正在整理回复...
data: [ANSWER]                            ← 最终答案开始
data: 亲爱的顾客，您好！
data: 您的订单 ORD002 ...
```

### Admin 接口配置（需携带 Token）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/mock-config` | 获取所有接口配置 |
| POST | `/api/mock-config/{operation}` | 保存/更新配置（upsert）|
| DELETE | `/api/mock-config/{operation}` | 重置为默认 Mock |
| POST | `/api/mock-config/{operation}/test` | 测试接口连通性 |

---

## 前端页面说明

### 登录页（`/login`）
- 支持登录 / 注册切换（Tab）
- 登录成功后 Token 存入 `localStorage`，自动跳转原目标页
- Vue 2 Options API，使用 Element UI 2.x 组件

### 用户客服页（`/`）

```
┌─────────────────┬──────────────────────────────────┐
│  新建会话        │  当前会话标题             [头部栏] │
│─────────────────│──────────────────────────────────│
│  历史会话列表    │                                  │
│  ┌────────────┐ │           消息气泡区              │
│  │ 会话标题   │ │                                  │
│  │ N分钟前    │ │                                  │
│  └────────────┘ │                                  │
│  ...            │──────────────────────────────────│
│─────────────────│  [输入框]          [发送]         │
│  发送模式切换   └──────────────────────────────────┘
│  快速提问
└─────────────────
```

| 功能 | 说明 |
|------|------|
| 历史会话列表 | 显示每条会话的第一个用户提问（截断）+ 相对时间 |
| 切换会话 | 点击任意历史条目，自动加载该会话完整聊天记录 |
| 新建会话 | 清空当前消息，生成新 sessionId |
| 发送模式 | 同步（等待完整回复）/ 流式（实时进度动画）|
| 快速提问 | 点击预设问题直接填入输入框 |

### Admin 配置页（`/admin`）
- Postman 风格：选择操作 → 配置 URL / Method / Headers / Body → 测试 → 保存
- 请求体支持 JSON 美化（`el-icon-magic-stick`）
- 响应面板可拖拽调整高度，支持放大/复制/关闭

---

## 项目结构

```
my-project/
├── codeAIGraph/                           # Spring Boot 后端
│   ├── pom.xml                            # 含 micrometer-tracing-bridge-brave
│   └── src/main/
│       ├── java/com/example/mallcs/
│       │   ├── MallCustomerServiceApplication.java  # 启动类（@MapperScan @EnableAsync）
│       │   ├── config/
│       │   │   ├── MallGraphConfig.java             # Graph 工作流装配
│       │   │   ├── SecurityConfig.java              # Security + CORS（暴露追踪头）
│       │   │   └── TracingRestTemplateConfig.java   # RestTemplate Bean + B3 拦截器
│       │   ├── filter/
│       │   │   └── TraceIdResponseFilter.java       # 所有响应注入 X-B3-TraceId
│       │   ├── controller/
│       │   │   ├── AuthController.java              # 登录/注册（AES Token）
│       │   │   ├── CustomerServiceController.java   # 聊天 + 历史（含 traceId 字段）
│       │   │   └── MockConfigController.java        # Admin 接口配置管理
│       │   ├── entity/                              # 普通 POJO（无 JPA 注解）
│       │   │   ├── AppUser.java                     # 用户（实现 UserDetails）
│       │   │   ├── ChatHistoryEntity.java           # 对话记录
│       │   │   └── MockApiConfigEntity.java         # 接口配置
│       │   ├── mapper/                              # MyBatis Mapper 接口
│       │   │   ├── UserMapper.java
│       │   │   ├── ChatHistoryMapper.java
│       │   │   └── MockApiConfigMapper.java
│       │   ├── typehandler/
│       │   │   └── JsonMapTypeHandler.java          # Map<String,String> ↔ JSON
│       │   ├── security/
│       │   │   ├── AesTokenUtil.java                # AES-256/CBC Token 生成/解析/验证
│       │   │   └── JwtAuthFilter.java               # Token 认证过滤器（使用 AesTokenUtil）
│       │   ├── service/
│       │   │   ├── MallDataService.java             # 数据服务接口
│       │   │   ├── ConfigurableMallDataService.java # 外部 API（@Primary，注入 tracingRestTemplate）
│       │   │   ├── MockMallDataService.java         # Mock 兜底实现
│       │   │   ├── MockApiConfigService.java        # 接口配置 CRUD
│       │   │   ├── UserService.java                 # 用户注册/登录
│       │   │   └── ChatHistoryAdvisor.java          # @Async 异步记录对话
│       │   ├── init/
│       │   │   └── DataInitializer.java             # 启动时初始化默认用户
│       │   └── graph/nodes/                         # Graph 节点
│       │       ├── IntentParserNode.java
│       │       ├── OrderStatusNode.java
│       │       ├── LogisticsQueryNode.java
│       │       ├── OrderReviewNode.java
│       │       ├── ResponseFormatterNode.java
│       │       └── GeneralAnswerNode.java
│       └── resources/
│           ├── application.yml                      # 含 management.tracing 配置
│           ├── mapper/                              # MyBatis SQL XML 映射文件
│           │   ├── UserMapper.xml
│           │   ├── ChatHistoryMapper.xml
│           │   └── MockApiConfigMapper.xml
│           └── sql/
│               ├── schema.sql                       # DDL（启动时自动执行）
│               └── data.sql                         # 初始数据
│
└── mall-frontend/                                   # Vue 2 前端
    ├── package.json                                 # vue@2.7, element-ui@2.15, vue-router@3.6
    ├── vite.config.js                               # host: 0.0.0.0 + @vitejs/plugin-vue2
    └── src/
        ├── main.js                                  # new Vue() + Vue.use(ElementUI)
        ├── views/
        │   ├── Login.vue                            # 登录/注册（Options API + Element UI）
        │   ├── UserChat.vue                         # 用户聊天（历史会话 + SSE 流式）
        │   └── AdminConfig.vue                      # Admin 接口配置（Postman 风格）
        ├── utils/
        │   └── auth.js                              # Token/User 本地存储 + logout 清理
        ├── api/
        │   └── index.js                             # Axios + 自动附加 Token + 401 跳转
        ├── router/
        │   └── index.js                             # Vue Router 3 + 登录守卫（beforeEach）
        └── App.vue                                  # 顶部导航 + 用户信息 + 退出登录
```

---

## 数据库表结构

```sql
-- 用户表
CREATE TABLE app_user (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  UNIQUE NOT NULL,
    password   VARCHAR(255) NOT NULL,           -- BCrypt 加密
    nickname   VARCHAR(100),
    role       VARCHAR(20)  DEFAULT 'USER',     -- USER / ADMIN
    enabled    BOOLEAN      DEFAULT TRUE,
    created_at TIMESTAMP    DEFAULT NOW()
);

-- 对话历史表
CREATE TABLE chat_history (
    id         BIGSERIAL PRIMARY KEY,
    user_id    VARCHAR(100) NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,           -- USER / ASSISTANT
    content    TEXT,
    scene_type VARCHAR(50),                     -- 意图场景（logistics_query 等）
    order_no   VARCHAR(50),
    created_at TIMESTAMP    DEFAULT NOW()
);

-- Mock 接口配置表
CREATE TABLE mock_api_config (
    id           BIGSERIAL PRIMARY KEY,
    operation    VARCHAR(50)  UNIQUE NOT NULL,  -- order_status / logistics_info / submit_review
    enabled      BOOLEAN      DEFAULT FALSE,
    description  VARCHAR(200),
    url          VARCHAR(500),
    method       VARCHAR(10)  DEFAULT 'GET',
    headers      JSONB,
    request_body TEXT,
    updated_at   TIMESTAMP    DEFAULT NOW()
);
```

---

## Mock 数据

| 订单号 | 商品 | 状态 | 物流 |
|--------|------|------|------|
| ORD001 | iPhone 16 Pro | 已完成 ✅ | 已签收（可评价）|
| ORD002 | Nike 跑步鞋 | 已发货 🚚 | 派送中（今天送达）|
| ORD003 | 小米平板 7 Pro | 待发货 📦 | 备货中 |
| ORD004 | Sony 耳机 | 待付款 💳 | 超时自动取消 |
| ORD005 | 戴森吸尘器 | 退款中 💰 | 3-5 工作日到账 |

---

## 如何接入真实商城系统

1. 在 Admin 后台（`/admin`）为每个操作配置真实 API 地址
2. 开启 **Enable** 开关启用外部 API 调用
3. 后端 `ConfigurableMallDataService`（`@Primary`）优先调用已启用的外部 API，**调用时自动携带 B3 追踪头**，失败时自动降级到 Mock

**无需修改任何 Graph 节点代码。**

---

## 常见问题

**Q: 启动报 `api-key` 相关错误？**
> 确认 `application.yml` 中 `spring.ai.deepseek.api-key` 已填入有效的 DeepSeek API Key。

**Q: 数据库连接失败？**
> 确认 PostgreSQL 已启动，`application.yml` 中用户名/密码与实际一致。  
> 若使用默认配置：用户名 `postgres`、密码 `123456`、数据库 `postgres`。

**Q: Maven 报 "无效的目标发行版: 17"？**
> Maven 使用的是低版本 JDK。设置方法：
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
> $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
> ```

**Q: 前端 `npm install` 报 peer dependency 冲突？**
> Vue 2 生态存在 peer dep 版本冲突，使用 `--legacy-peer-deps` 参数：
> ```powershell
> npm install --legacy-peer-deps
> ```

**Q: 前端无法从其他设备访问？**
> `vite.config.js` 已配置 `host: '0.0.0.0'`，确认 Windows 防火墙放行 3000 端口：
> ```powershell
> New-NetFirewallRule -DisplayName "Vite Dev 3000" -Direction Inbound -Protocol TCP -LocalPort 3000 -Action Allow
> ```

**Q: 流式聊天没有输出？**
> 检查后端是否启动（8080 端口）、Token 是否有效（查看浏览器 Network 面板请求头）。  
> SSE 事件格式：每行 `data: [PROGRESS] ...` 或 `data: [ANSWER]` 后跟回答内容。

**Q: 响应头中没有 `X-B3-TraceId`？**
> 确认 `spring-boot-starter-actuator` 和 `micrometer-tracing-bridge-brave` 已引入（`pom.xml`）。  
> 确认 `management.tracing.sampling.probability` 大于 0。  
> 若跨域读取头失败，检查 `SecurityConfig.java` 的 `setExposedHeaders` 包含 `X-B3-TraceId`。

**Q: MyBatis 查询结果字段为 null？**
> 已配置 `map-underscore-to-camel-case: true`，数据库下划线字段（如 `user_id`）自动映射到 Java 驼峰字段（`userId`）。若仍为 null，检查对应 `*Mapper.xml` 的 `resultMap` 定义。

**Q: 如何接入通义千问（DashScope）？**
> 将 `pom.xml` 中 `spring-ai-starter-model-deepseek` 替换为 `spring-ai-alibaba-starter-dashscope`，  
> 修改 `application.yml` 中的 `api-key` 和模型名，Graph 节点代码**无需任何改动**。

**Q: 如何调整 Token 有效期？**
> 修改 `application.yml` 中的 `token.expiration`（毫秒），例如 `7200000` = 2 小时。

**Q: 如何降低生产环境链路追踪开销？**
> 修改 `application.yml` 中的 `management.tracing.sampling.probability`，建议生产环境设为 `0.1`（10% 采样）。
