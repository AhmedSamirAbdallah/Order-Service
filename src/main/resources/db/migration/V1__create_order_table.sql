CREATE TABLE orders (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_number VARCHAR(255) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    order_date TIMESTAMP NOT NULL,
    status VARCHAR(255) NOT NULL CHECK (status IN ('PENDING', 'SHIPPED', 'DELIVERED', 'CANCELED')),
    total_amount NUMERIC(12, 2) NOT NULL,
    shipping_address VARCHAR(255) NOT NULL,
    payment_method VARCHAR(255) NOT NULL CHECK (payment_method IN ('CREDIT_CARD', 'DEBIT_CARD', 'PAYPAL', 'BANK_TRANSFER')),
    shipping_cost NUMERIC(12, 2),
    discount NUMERIC(12, 2),
    tax_amount NUMERIC(12, 2) NOT NULL,
    order_source VARCHAR(255) CHECK (order_source IN ('WEBSITE', 'MOBILE_APP', 'IN_STORE')),
    notes TEXT
);
