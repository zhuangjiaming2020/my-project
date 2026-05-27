# 商城智能客服 —— Spring AI Alibaba Graph + DeepSeek 完整实战

> 涵盖：多场景意图识别 → Graph 工作流 → 可配置外部 API → JWT 用户登录 → PostgreSQL 对话持久化

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 3.5.0 | Web 框架 |
| Spring AI Alibaba | 1.1.2.0 | Graph 工作流引擎 |
| Spring AI | 1.1.2 | AI 抽象层 |
| DeepSeek | deepseek-chat | 大语言模型 |
| MyBatis | 3.0.4 | SQL 映射持久化框架 |
| PostgreSQL | 15+ | 关系型数据库 |
| Spring Security | 6.x | 安全框架 |
| JJWT | 0.12.x | JWT 令牌 |
| Vue 3 | 3.x | 前端框架 |
| Element Plus | 2.x | UI 组件库 |

---

## 系统架构

```
用户浏览器
   │
   ▼ (JWT Token)
[Vue3 前端 :5173]
   │  /api/auth/**   → 登录 / 注册
   │  /api/chat      → 聊天（同步 / SSE 流式）
   │  /api/mock-config → Admin 接口配置管理
   ▼
[Spring Boot 后端 :8080]
   │
   ├── Spring Security (JWT 鉴权)
   ├── CustomerServiceController
   │      └── Spring AI Graph
   │              ├── IntentParserNode    ← LLM 意图识别
   │              ├── OrderStatusNode     ← 查订单状态
   │              ├── LogisticsQueryNode  ← 查物流
   │              ├── OrderReviewNode     ← 提交评价
   │              ├── ResponseFormatterNode ← LLM 格式化回复
   │              └── GeneralAnswerNode   ← 通用问答 LLM
   ├── ChatHistoryAdvisor  → 异步持久化对话记录
   └── MockApiConfigService → 从 DB 读取可配置外部 API
          └── ConfigurableMallDataService (优先) / MockMallDataService (兜底)
                                  ↕
                           [PostgreSQL :5432]
                           ├── app_user       （用户账号）
                           ├── chat_history   （对话记录）
                           └── mock_api_config（接口配置）
```

---

## 工作流（Graph）

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
- **Maven 3.8+**（或使用项目内 mvnw）
- **PostgreSQL 15+**（本地运行，默认端口 5432）
- **Node.js 18+**（前端）
- **DeepSeek API Key**（[申请地址](https://platform.deepseek.com/)）

### 1. 准备数据库

```sql
-- 连接到 PostgreSQL，创建数据库和用户
CREATE DATABASE mall_cs;
CREATE USER mall_user WITH PASSWORD 'mall_pass';
GRANT ALL PRIVILEGES ON DATABASE mall_cs TO mall_user;
```

> 表结构和初始数据由 Spring Boot 启动时自动执行  
> `src/main/resources/sql/schema.sql` 和 `src/main/resources/sql/data.sql`

### 2. 配置环境变量

**Windows（PowerShell）：**
```powershell
$env:DEEPSEEK_API_KEY = "sk-your-api-key-here"
```

**macOS / Linux：**
```bash
export DEEPSEEK_API_KEY=sk-your-api-key-here
```

> 或直接修改 `src/main/resources/application.yml`（不要提交到 Git）

### 3. 启动后端

```powershell
cd my-project/codeAIGraph

# 设置 Java 17（若系统默认 JDK 版本较低）
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 编译并运行
mvn spring-boot:run
```

后端启动成功后输出：`Started MallCustomerServiceApplication`，默认监听 `http://localhost:8080`

### 4. 启动前端

```powershell
cd my-project/mall-frontend
npm install    # 首次运行需要
npm run dev
```

前端启动后访问 `http://localhost:5173`

---

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| user1 | user123 | 普通用户 |

> 账号由 `DataInitializer` 在应用启动时自动创建（密码 BCrypt 加密）

---

## 接口说明

### 认证接口（无需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT Token |
| POST | `/api/auth/register` | 注册新用户 |
| GET | `/api/auth/me` | 获取当前用户信息（需 Token） |

**登录示例：**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"user123"}'
```

返回：
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": { "id": 2, "username": "user1", "nickname": "用户1", "role": "USER" }
}
```

### 聊天接口（需携带 JWT）

所有请求需加 Header：`Authorization: Bearer <token>`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 同步聊天 |
| POST | `/api/chat/stream` | 流式聊天（SSE） |
| GET | `/api/chat/history/{sessionId}` | 获取会话历史 |
| GET | `/api/chat/sessions` | 获取历史会话列表 |

**聊天示例：**
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"message": "帮我查一下 ORD002 的快递到哪了", "sessionId": "session001"}'
```

### Admin 接口配置（需携带 JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/mock-config` | 获取所有接口配置 |
| POST | `/api/mock-config/{operation}` | 保存/更新配置 |
| DELETE | `/api/mock-config/{operation}` | 重置为默认 |
| POST | `/api/mock-config/{operation}/test` | 测试接口连通性 |

---

## 项目结构

```
my-project/
├── codeAIGraph/                          # Spring Boot 后端
│   └── src/main/java/com/example/mallcs/
│       ├── MallCustomerServiceApplication.java   # 启动类（含 @MapperScan）
│       ├── config/
│       │   ├── MallGraphConfig.java      # Graph 工作流装配
│       │   └── SecurityConfig.java       # Spring Security + CORS + JWT
│       ├── controller/
│       │   ├── AuthController.java       # 登录/注册
│       │   ├── CustomerServiceController.java  # 聊天 + 历史查询
│       │   └── MockConfigController.java # Admin 接口配置管理
│       ├── entity/                       # 普通 POJO（无 JPA 注解）
│       │   ├── AppUser.java              # 用户（实现 UserDetails）
│       │   ├── ChatHistoryEntity.java    # 对话记录
│       │   └── MockApiConfigEntity.java  # 接口配置
│       ├── mapper/                       # MyBatis Mapper 接口
│       │   ├── UserMapper.java
│       │   ├── ChatHistoryMapper.java
│       │   └── MockApiConfigMapper.java
│       ├── typehandler/
│       │   └── JsonMapTypeHandler.java   # Map<String,String> ↔ JSON TypeHandler
│       ├── security/
│       │   ├── JwtUtil.java              # JWT 生成/解析/验证
│       │   └── JwtAuthFilter.java        # JWT 认证过滤器
│       ├── service/
│       │   ├── MallDataService.java      # 数据服务接口
│       │   ├── ConfigurableMallDataService.java  # 调用外部 API（@Primary）
│       │   ├── MockMallDataService.java  # Mock 兜底
│       │   ├── MockApiConfigService.java # 接口配置 CRUD
│       │   ├── UserService.java          # 用户注册/登录
│       │   └── ChatHistoryAdvisor.java   # 异步记录对话
│       ├── init/
│       │   └── DataInitializer.java      # 启动时初始化默认用户
│       └── graph/nodes/                  # Graph 节点
│           ├── IntentParserNode.java
│           ├── OrderStatusNode.java
│           ├── LogisticsQueryNode.java
│           ├── OrderReviewNode.java
│           ├── ResponseFormatterNode.java
│           └── GeneralAnswerNode.java
│   └── src/main/resources/
│       ├── application.yml
│       ├── mapper/                       # MyBatis SQL XML 映射文件
│       │   ├── UserMapper.xml
│       │   ├── ChatHistoryMapper.xml
│       │   └── MockApiConfigMapper.xml
│       └── sql/
│           ├── schema.sql                # DDL（自动执行）
│           └── data.sql                  # 初始数据（自动执行）
│
└── mall-frontend/                        # Vue 3 前端
    └── src/
        ├── views/
        │   ├── Login.vue                 # 登录/注册页
        │   ├── UserChat.vue              # 用户聊天界面（SSE 流式）
        │   └── AdminConfig.vue           # Admin 接口配置（Postman 风格）
        ├── utils/
        │   └── auth.js                   # JWT 本地存储工具
        ├── api/
        │   └── index.js                  # Axios + 自动附加 JWT + 401 跳转
        ├── router/
        │   └── index.js                  # 路由 + 登录守卫
        └── App.vue                       # 顶部导航 + 用户信息 + 退出
```

---

## 数据库表结构

```sql
-- 用户表
app_user (id, username, password, nickname, role, enabled, created_at)

-- 对话历史表
chat_history (id, user_id, session_id, role, content, scene_type, order_no, created_at)

-- Mock 接口配置表
mock_api_config (id, operation, enabled, description, url, method, headers, request_body, updated_at)
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
2. 设置 **Enable** 开关开启该操作的外部 API 调用
3. 后端 `ConfigurableMallDataService` 会优先调用已启用的外部 API，失败时自动降级到 Mock

无需修改任何 Graph 节点代码。

---

## 常见问题

**Q: 启动报 `api-key` 相关错误？**
> 确认已设置 `DEEPSEEK_API_KEY` 环境变量，值为有效的 DeepSeek API Key。

**Q: 数据库连接失败？**
> 确认 PostgreSQL 已启动，数据库 `mall_cs` 已创建，`application.yml` 中用户名/密码与实际一致。

**Q: Maven 报 "无效的目标发行版: 17"？**
> Maven 使用的是 JDK 8。需设置：
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
> $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
> ```

**Q: 依赖下载缓慢？**
> `pom.xml` 已配置阿里云镜像，若仍慢可在 `~/.m2/settings.xml` 中手动添加。

**Q: MyBatis 查询结果字段为 null？**
> 已配置 `map-underscore-to-camel-case: true`，数据库下划线命名（如 `user_id`）会自动映射到 Java 驼峰字段（`userId`）。若仍有问题，检查对应 `*Mapper.xml` 中的 `resultMap` 定义。

**Q: 如何新增数据库操作？**
> 在 `mapper/` 下的接口添加方法，在 `resources/mapper/` 对应 XML 中编写 SQL，然后在 Service 中调用即可。

**Q: 如何接入通义千问（DashScope）？**
> 将 `pom.xml` 中 `spring-ai-starter-model-deepseek` 替换为 `spring-ai-alibaba-starter-dashscope`，
> 修改 `application.yml` 中的 `api-key`，Graph 节点代码无需任何改动。
