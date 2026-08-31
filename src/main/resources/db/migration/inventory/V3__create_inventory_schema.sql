create schema
if not exists inventory;

create table stock (
    id                 uuid    primary key,
    product_id         uuid    not null unique,
    available_quantity integer not null,
    reserved_quantity  integer not null,
    version            bigint  not null
);

create table reservation (
    id                 uuid                      primary key,
    order_id           uuid                      not null,
    product_id         uuid                      not null,
    quantity           integer                   not null,
    reservation_status varchar(255)              not null,
    created_at         timestamp with time zone  not null
);

create index idx_reservation_order_id on reservation (order_id);

alter table stock
SET schema inventory;

alter table reservation
SET schema inventory;
