alter table inventory.stock
add column price numeric(19, 2) not null;


insert into inventory.stock (id, product_id, price, available_quantity, reserved_quantity, version) values
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 100, 100, 0, 0),
    ('22222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 200, 50, 0, 0),
    ('33333333-3333-3333-3333-333333333333', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 300, 3, 0, 0);

