create schema
if not exists order_service;

alter table orders
SET schema order_service;

alter table order_item
SET schema order_service;