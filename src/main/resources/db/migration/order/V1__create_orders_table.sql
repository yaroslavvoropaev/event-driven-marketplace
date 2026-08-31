create table orders (
    id            uuid                     primary key,
    customer_id   varchar(255)             not null,
    order_status  varchar(20)              not null,
    created_at    timestamp with time zone not null
);

create table order_item (
    id           uuid           primary key,
    order_id     uuid           not null references orders (id),
    product_id   uuid           not null,
    quantity     integer        not null,
    unit_price   numeric(19, 2) not null
);

create index idx_order_item_order_id on order_item (order_id);
