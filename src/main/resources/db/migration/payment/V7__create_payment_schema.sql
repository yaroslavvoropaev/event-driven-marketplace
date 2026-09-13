create schema
if not exists payment_service;

create table payment (
    id                     uuid                     primary key,
    order_id               uuid                     not null unique,
    customer_id            uuid                     not null,
    amount                 numeric(19, 2)           not null,
    payment_status         varchar(255)             not null,
    gateway_transaction_id varchar(255),
    failure_reason         varchar(512),
    created_at             timestamp with time zone not null,
    updated_at             timestamp with time zone not null
);

alter table payment
set schema payment_service;
