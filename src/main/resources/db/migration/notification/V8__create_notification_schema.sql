create schema
if not exists notification_service;

create table notifications (
    id                     uuid                     primary key,
    order_id               uuid                     not null,
    customer_id            uuid                     not null,
    type                   varchar(255)             not null,
    recipient              varchar(255)             not null,
    status                 varchar(255)             not null,
    subject                varchar(255)             not null,
    body                   text                     not null,
    failure_reason         varchar(512),
    created_at             timestamp with time zone not null,
    sent_at                timestamp with time zone,

    constraint uk_notification_order_type unique (order_id, type)
);

alter table notifications
set schema notification_service;