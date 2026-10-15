create schema if not exists messaging;

create table messaging.outbox (
    id           bigserial                primary key,
    message_type varchar(255)             not null,
    order_id     uuid                     not null,
    payload      text                     not null,
    created_at   timestamp with time zone not null,
    sent_at      timestamp with time zone,
    attempts     int                      not null default 0,
    last_error   varchar(1024)
);

create index idx_outbox_unsent on messaging.outbox (id) where sent_at is null;