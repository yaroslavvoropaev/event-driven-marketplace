CREATE TABLE orders (
    id            UUID PRIMARY KEY,
    customer_id   VARCHAR(255)   NOT NULL,
    order_status  VARCHAR(20)    NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL
);

CREATE TABLE order_item (
    id           UUID PRIMARY KEY,
    order_id     UUID           NOT NULL REFERENCES orders (id),
    product_id   UUID           NOT NULL,
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(19, 2) NOT NULL
);

CREATE INDEX idx_order_item_order_id ON order_item (order_id);
