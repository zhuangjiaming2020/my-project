# 商城智能客服 —— Spring AI Alibaba Graph + DeepSeek 完整实战

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 3.5.0 | Web 框架 |
| Spring AI Alibaba | 1.1.2.0 | Graph 工作流引擎 |
| Spring AI | 1.1.2 | AI 抽象层 |
| DeepSeek | deepseek-chat | 大语言模型 |
| Maven | 3.8+ | 构建工具 |

---

## 工作流架构

```
用户输入
   │
   ▼
[intent_parser]  ← DeepSeek LLM 识别场景、提取订单号/评分等实体
   │
   ├─ scene_type=logistics_query ──► [logistics_query_node]  ← Mock数据库查物流
   │                                          │
   ├─ scene_type=order_status    ──► [order_status_node]     ← Mock数据库查订单
   │                                          │
   ├─ scene_type=order_review    ──► [order_review_node]     ← Mock数据库提交评价
   │                                          │
   │                              [response_formatter] ← DeepSeek 生成自然语言回复
   │                                          │
   └─ scene_type=general         ──► [general_answer_node]  ← DeepSeek 直接回答
                                              │
                                             END → 返回 final_answer
```

### 支持的场景

| 场景 | scene_type | 示例输入 |
|------|-----------|---------|
| 物流查询 | logistics_query | "ORD002 的快递到哪了" |
| 订单状态 | order_status | "查一下订单 ORD003 状态" |
| 订单评价 | order_review | "我想给 ORD001 评个五星好评" |
| 通用咨询 | general | "怎么申请退款" / "你们支持哪些支付方式" |

---

## 快速启动

### 1. 获取 DeepSeek API Key

前往 [DeepSeek 开放平台](https://platform.deepseek.com/) 注册并获取 API Key。

### 2. 设置环境变量

**Windows（PowerShell）：**
```powershell
$env:DEEPSEEK_API_KEY = "sk-your-api-key-here"
```

**Windows（CMD）：**
```cmd
set DEEPSEEK_API_KEY=sk-your-api-key-here
```

**macOS / Linux：**
```bash
export DEEPSEEK_API_KEY=sk-your-api-key-here
```

> 也可以直接修改 `src/main/resources/application.yml` 中的 `api-key` 字段（不要提交到 Git）

### 3. 编译并运行

```bash
# 进入项目目录
cd e:/zjm/AI/codeAIGraph

# 编译
mvn clean package -DskipTests

# 运行（确保已设置 DEEPSEEK_API_KEY 环境变量）
java -jar target/mall-customer-service-1.0.0-SNAPSHOT.jar

# 或直接用 Maven 运行
mvn spring-boot:run
```

应用启动后访问 `http://localhost:8080`

---

## 接口测试

### 方式一：curl 命令行

**查询物流（ORD002 — 派送中）：**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我查一下 ORD002 的快递到哪了", "sessionId": "user001"}'
```

**查询订单状态（ORD003 — 待发货）：**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "我的订单 ORD003 发货了吗", "sessionId": "user001"}'
```

**提交评价（ORD001 — 已完成，可评价）：**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "我想给订单 ORD001 打五星好评，商品很棒物流很快", "sessionId": "user001"}'
```

**通用咨询：**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你们支持货到付款吗", "sessionId": "user001"}'
```

**演示接口（GET，最简单）：**
```bash
curl "http://localhost:8080/api/chat/demo?message=查询ORD002物流"
```

### 方式二：流式接口（SSE）

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "ORD001 到了吗"}' \
  --no-buffer
```

### 方式三：Postman

- Method: POST
- URL: `http://localhost:8080/api/chat`
- Body (JSON): `{"message": "查订单 ORD002 的物流", "sessionId": "test"}`

---

## Mock 数据说明

| 订单号 | 商品 | 状态 | 物流 | 备注 |
|--------|------|------|------|------|
| ORD001 | iPhone 16 Pro | 已完成 ✅ | 已签收 | 可评价 |
| ORD002 | Nike 跑步鞋 | 已发货 🚚 | 派送中 | 预计今天送达 |
| ORD003 | 小米平板 7 Pro | 待发货 📦 | 暂无 | 备货中 |
| ORD004 | Sony 耳机 | 待付款 💳 | 暂无 | 超时自动取消 |
| ORD005 | 戴森吸尘器 | 退款中 💰 | 暂无 | 3-5工作日到账 |

---

## 切换生产数据

Mock 数据服务 `MockMallDataService` 的三个方法对应生产替换点：

```java
// 1. 替换为数据库查询（如 MyBatis/JPA）
public Optional<Order> getOrderStatus(String orderId) {
    // return orderMapper.findById(orderId);   ← 生产代码
}

// 2. 替换为物流 API 调用（如快递100、菜鸟等）
public Optional<Logistics> getLogisticsInfo(String orderId) {
    // return logisticsApiClient.query(orderId);  ← 生产代码
}

// 3. 替换为数据库写入
public ReviewResult submitReview(String orderId, int rating, String content) {
    // return reviewRepository.save(new ReviewEntity(...));  ← 生产代码
}
```

**Graph 节点层（LogisticsQueryNode、OrderStatusNode、OrderReviewNode）完全无需修改。**

---

## 项目结构

```
src/main/java/com/example/mallcs/
├── MallCustomerServiceApplication.java    # 启动类
├── config/
│   └── MallGraphConfig.java               # Graph 工作流装配（节点注册、边连接、编译）
├── controller/
│   └── CustomerServiceController.java     # REST API（同步 + SSE 流式）
├── domain/                                # 领域模型
│   ├── Order.java
│   ├── Logistics.java
│   ├── LogisticsEvent.java
│   └── ReviewResult.java
├── graph/
│   ├── MallStateFactory.java              # Graph 状态键策略定义
│   └── nodes/
│       ├── IntentParserNode.java          # 意图解析（LLM）
│       ├── LogisticsQueryNode.java        # 物流查询（DB）
│       ├── OrderStatusNode.java           # 订单状态查询（DB）
│       ├── OrderReviewNode.java           # 订单评价提交（DB）
│       ├── GeneralAnswerNode.java         # 通用问答（LLM）
│       └── ResponseFormatterNode.java     # 回复格式化（LLM）
└── mock/
    └── MockMallDataService.java           # Mock 数据服务（替换此处接入生产 DB）
```

---

## 常见问题

**Q: 启动报错 `api-key` 相关？**
> 请确认已设置 `DEEPSEEK_API_KEY` 环境变量，值为有效的 DeepSeek API Key。

**Q: 依赖下载失败？**
> `pom.xml` 已配置 Spring Milestones 和阿里云中央仓库镜像，若仍失败可手动在 `~/.m2/settings.xml` 配置阿里云镜像。

**Q: 如何查看 Graph 执行路径？**
> 日志级别默认 DEBUG（`com.example.mallcs`），每个节点执行时会打印 `[NodeName] xxx` 日志。

**Q: 如何接入 DashScope（通义千问）？**
> 将 `pom.xml` 中的 `spring-ai-starter-model-deepseek` 替换为 `spring-ai-alibaba-starter-dashscope`，
> 并修改 `application.yml` 中的 `spring.ai.dashscope.api-key`，其余 Graph 代码无需改动。
