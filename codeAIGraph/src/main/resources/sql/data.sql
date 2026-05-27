-- ================================================================
-- 智能运维客服系统 - 初始化数据
-- 用户数据由 DataInitializer.java 通过 BCrypt 动态生成
-- ================================================================

-- ----------------------------------------------------------------
-- Mock 接口配置初始数据（三个操作的默认配置，均为关闭状态）
-- ----------------------------------------------------------------
INSERT INTO mock_api_config (operation, enabled, description, url, method, headers, request_body)
VALUES
    ('order_status',
     false,
     '查询订单状态接口',
     'http://your-mall-api.com/orders/{orderId}/status',
     'GET',
     '{}',
     NULL),
    ('logistics_info',
     false,
     '查询物流信息接口',
     'http://your-mall-api.com/orders/{orderId}/logistics',
     'GET',
     '{}',
     NULL),
    ('submit_review',
     false,
     '提交订单评价接口',
     'http://your-mall-api.com/orders/review',
     'POST',
     '{}',
     '{"orderId": "{{orderId}}", "rating": {{rating}}, "content": "{{content}}"}')
ON CONFLICT (operation) DO NOTHING;
