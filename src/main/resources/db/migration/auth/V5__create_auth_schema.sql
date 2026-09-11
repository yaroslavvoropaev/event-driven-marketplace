create schema
if not exists auth;

create table users (
    id                 uuid                     primary key,
    email              varchar(256)             not null unique,
    password_hash     varchar(256)              not null,
    created_at          timestamp with time zone not null
);

alter table users
SET schema auth;
