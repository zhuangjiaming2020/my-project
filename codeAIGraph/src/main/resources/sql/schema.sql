-- ================================================================
-- 智能运维客服系统 - 数据库建表脚本
-- 数据库: PostgreSQL 14+
-- 每次启动幂等执行（IF NOT EXISTS / ON CONFLICT）
-- ================================================================

-- ----------------------------------------------------------------
-- 1. 用户表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_user (
    id          VARCHAR(64)  PRIMARY KEY,
    username    VARCHAR(64)  UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    nickname    VARCHAR(64),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  app_user             IS '系统用户表';
COMMENT ON COLUMN app_user.id          IS '用户唯一ID（UUID）';
COMMENT ON COLUMN app_user.username    IS '登录用户名';
COMMENT ON COLUMN app_user.password    IS 'BCrypt 加密密码';
COMMENT ON COLUMN app_user.role        IS '角色: USER / ADMIN';

-- ----------------------------------------------------------------
-- 2. 对话历史表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_history (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     VARCHAR(64)  NOT NULL,
    session_id  VARCHAR(64)  NOT NULL,
    role        VARCHAR(20)  NOT NULL,   -- USER / ASSISTANT
    content     TEXT         NOT NULL,
    scene_type  VARCHAR(50),             -- order_status / logistics_query / order_review / general
    order_no    VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_chat_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_user_session  ON chat_history(user_id, session_id);
CREATE INDEX IF NOT EXISTS idx_chat_user_created  ON chat_history(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_session       ON chat_history(session_id);

COMMENT ON TABLE  chat_history            IS '用户对话历史记录';
COMMENT ON COLUMN chat_history.user_id    IS '所属用户ID';
COMMENT ON COLUMN chat_history.session_id IS '会话ID（同一轮对话共享）';
COMMENT ON COLUMN chat_history.role       IS '消息角色: USER=用户 ASSISTANT=助手';
COMMENT ON COLUMN chat_history.scene_type IS '场景类型（从AI响应中提取）';

-- ----------------------------------------------------------------
-- 3. Mock 接口配置表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mock_api_config (
    id           BIGSERIAL    PRIMARY KEY,
    operation    VARCHAR(50)  UNIQUE NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    description  VARCHAR(255),
    url          VARCHAR(500),
    method       VARCHAR(10)  NOT NULL DEFAULT 'GET',
    headers      TEXT         NOT NULL DEFAULT '{}',  -- JSON 格式
    request_body TEXT,
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  mock_api_config             IS 'Mock数据接口配置表';
COMMENT ON COLUMN mock_api_config.operation   IS '操作标识: order_status / logistics_info / submit_review';
COMMENT ON COLUMN mock_api_config.enabled     IS '是否启用外部API（false=使用Mock数据）';
COMMENT ON COLUMN mock_api_config.headers     IS '请求头（JSON键值对）';
COMMENT ON COLUMN mock_api_config.request_body IS 'POST请求体模板，支持{{orderId}}等占位符';
