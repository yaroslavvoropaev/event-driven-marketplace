create schema
if not exists order_service;

alter table orders
set schema order_service;

alter table order_item
set schema order_service;