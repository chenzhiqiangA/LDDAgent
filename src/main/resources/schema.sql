CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    session_mode VARCHAR(16) NOT NULL,
    selected_capability_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_chat_session_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    message_role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    capability_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_chat_message_session_created (session_id, created_at),
    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id) REFERENCES chat_session (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL COMMENT '订单号',
    user_id VARCHAR(64) NOT NULL COMMENT '下单用户',
    order_status VARCHAR(32) NOT NULL COMMENT '订单状态：PENDING_PAY待支付 PAID已支付 FULFILLING履约中 COMPLETED已完成 CANCELLED已取消 CLOSED已关闭',
    total_amount DECIMAL(12, 2) NOT NULL COMMENT '订单总额',
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    payable_amount DECIMAL(12, 2) NOT NULL COMMENT '应付金额',
    currency CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    pay_status VARCHAR(32) NOT NULL COMMENT '支付状态：UNPAID未支付 PAYING支付中 PAID已支付 REFUNDING退款中 REFUNDED已退款 PAY_FAILED支付失败',
    pay_method VARCHAR(32) NULL COMMENT '支付方式：WECHAT ALIPAY CARD BALANCE',
    pay_amount DECIMAL(12, 2) NULL COMMENT '实付金额',
    pay_trade_no VARCHAR(64) NULL COMMENT '第三方支付流水号',
    pay_channel VARCHAR(32) NULL COMMENT '支付渠道',
    pay_time DATETIME NULL COMMENT '支付完成时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_orders_order_no (order_no),
    UNIQUE KEY uk_orders_pay_trade_no (pay_trade_no),
    INDEX idx_orders_user_created (user_id, created_at),
    INDEX idx_orders_status_created (order_status, created_at),
    INDEX idx_orders_pay_status (pay_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
