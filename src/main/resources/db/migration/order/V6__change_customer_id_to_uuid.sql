alter table order_service.orders
    alter column customer_id type uuid using customer_id::uuid;