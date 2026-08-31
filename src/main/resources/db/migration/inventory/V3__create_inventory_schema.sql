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

insert into inventory.stock (id, product_id, available_quantity, reserved_quantity, version) values
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 100, 0, 0),
    ('22222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 50, 0, 0),
    ('33333333-3333-3333-3333-333333333333', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 3, 0, 0);

