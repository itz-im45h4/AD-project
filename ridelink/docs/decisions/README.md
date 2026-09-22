# Architecture Decision Records

Record decisions that affect more than one service. Use one short Markdown file per decision, named `ADR-001-title.md`.

Initial agreed decisions:

1. Four independently executable Spring Boot 4.1.1 services in one Maven reactor.
2. MongoDB Atlas with one database and scoped credentials per service.
3. Ride Management uses synchronous REST for eligible-driver lookup and final-fare calculation.
4. Fare & Payment publishes `PaymentRecorded` through RabbitMQ; Ride Management consumes it asynchronously.
5. Services share JWT verification configuration but never share persistence access.

Each new ADR should state the context, decision, consequences, date, and group agreement.
