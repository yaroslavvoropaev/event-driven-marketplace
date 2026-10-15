alter table order_service.orders
add column version bigint  not null default 0;

