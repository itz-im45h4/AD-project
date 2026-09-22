# RideLink Architecture

```mermaid
flowchart LR
    Client[Swagger UI / Postman]
    Account[Account Service\n:8081\naccount_db]
    Driver[Driver & Vehicle Service\n:8082\ndriver_vehicle_db]
    Ride[Ride Management Service\n:8083\nride_mgmt_db]
    Fare[Fare & Payment Service\n:8084\nfare_payment_db]
    Broker[(RabbitMQ)]

    Client --> Account
    Client --> Driver
    Client --> Ride
    Client --> Fare
    Ride -->|REST: available drivers| Driver
    Ride -->|REST: final fare| Fare
    Fare -->|PaymentRecorded| Broker
    Broker -->|PaymentRecorded| Ride
```

Each service owns its database. Services exchange UUID references only and never read or write another service's data store.

Driver & Vehicle answers who is available; Ride Management decides who is assigned. Fare & Payment owns money records; Ride Management owns the ride lifecycle.
