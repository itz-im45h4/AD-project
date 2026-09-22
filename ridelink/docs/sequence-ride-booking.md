# Ride Booking and Payment Sequence

```mermaid
sequenceDiagram
    participant P as Passenger
    participant R as Ride Management
    participant D as Driver & Vehicle
    participant F as Fare & Payment
    participant Q as RabbitMQ

    P->>R: POST /api/v1/rides
    R->>D: GET /api/v1/drivers/available?zone=...
    D-->>R: eligible driver UUIDs
    R-->>P: ride created / assigned
    Note over R: REQUESTED → ASSIGNED → ACCEPTED → IN_PROGRESS
    R->>F: POST /api/v1/fares/finalize
    F-->>R: final fare
    R-->>P: completed ride with final fare
    F->>Q: PaymentRecorded
    Q->>R: PaymentRecorded
    Note over R: payment settlement status updated asynchronously
```

REST is used where Ride Management needs an immediate response. RabbitMQ is used for the settlement notification because it can be retried without blocking ride completion.
