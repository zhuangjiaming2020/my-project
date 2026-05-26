package com.example.mallcs.service;

/**
 * 商城数据服务接口。
 *
 * <p>定义三类查询操作，返回格式化字符串供 Graph 节点直接写入 query_result。
 * <ul>
 *   <li>默认实现：{@link com.example.mallcs.mock.MockMallDataService}（内置 Mock 数据）</li>
 *   <li>可配置实现：{@link ConfigurableMallDataService}（优先调用外部 API，降级到 Mock）</li>
 * </ul>
 * 替换为真实数据库/API 时，只需实现本接口，无需修改 Graph 节点。
 */
public interface MallDataService {

    /**
     * 查询订单状态，返回格式化文本。
     *
     * @param orderId 订单号
     * @return 可直接放入 query_result 的文本
     */
    String queryOrderStatus(String orderId);

    /**
     * 查询物流信息，返回格式化文本。
     *
     * @param orderId 订单号
     * @return 可直接放入 query_result 的文本
     */
    String queryLogisticsInfo(String orderId);

    /**
     * 提交订单评价，返回格式化结果文本。
     *
     * @param orderId 订单号
     * @param rating  星级 1-5
     * @param content 评价内容
     * @return 可直接放入 query_result 的文本
     */
    String submitOrderReview(String orderId, int rating, String content);
}
