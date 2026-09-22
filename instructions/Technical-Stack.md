# RideLink — Technical Stack (Finalized)

> **IT3130 — Application Development | Group Assignment | RideLink Backend Microservices**

---

## 1. Database Call — MongoDB Atlas for All Four Services

**Decision:** MongoDB Atlas for all four services rather than mixing in a Postgres-as-a-service equivalent.


**Defensibility in Report:**

- Uniform document modeling chosen for iteration velocity under a compressed timeline
- Isolation satisfied by giving each service its own database + scoped credentials inside one cluster — not one shared database
- Compliant with §6.1 Data Ownership: one cluster, **4 databases** (one per service), each with a dedicated DB user scoped only to its own database
- Permitted by brief: MongoDB explicitly allowed "where appropriate"

## 2. Repository Call — Single Maven Multi-Module Monorepo

**Decision:** Confirmed compliant single shared Git repository.

> §6.4 of the brief literally requires "one shared Git repository accessible to the teaching team." A single Maven multi-module monorepo is not just permitted, it's the interpretation the brief is written for.

- One GitHub repo = satisfies Courseweb + teaching team access requirement
- Monorepo != Monolith — 4 independently executable Spring Boot services, independent DBs, REST + async communication
- Parent POM manages BOM, plugin versions, shared dependencies

---

## 3. Finalized Stack

| Layer | Choice |
| :--- | :--- |
| **Language / Framework** | Java 21, Spring Boot 4.1.1 |
| **Build** | Maven multi-module monorepo |
| **Database** | MongoDB Atlas, one cluster (M0 free tier), 4 databases — one per service, each with a dedicated DB user scoped only to its own database |
| **ODM** | Spring Data MongoDB |
| **Async Messaging** | RabbitMQ via CloudAMQP (hosted free tier — same "no server to manage" pattern as Atlas), using Spring AMQP starter |
| **Auth** | JWT issued by Account Service (HMAC secret in env var); Driver&Vehicle, Ride Mgmt, Fare&Payment validate the same secret as a Spring Security filter |
| **Sync Interservice Calls** | Spring RestClient — Ride Mgmt → Driver&Vehicle (eligible drivers), Ride Mgmt → Fare&Payment (final fare calculation - sync, needs immediate response) |
| **Async Events** | Fare&Payment publishes `PaymentRecorded` → Ride Mgmt consumes (async, fire-and-forget confirmation) |
| **API Docs** | springdoc-openapi (auto Swagger UI per service, no manual spec writing) |
| **Testing** | JUnit 5 + Mockito (unit); Testcontainers for Mongo/RabbitMQ (integration) — *Note: Testcontainers spins up local Mongo/RabbitMQ containers for tests, separate from your Atlas/CloudAMQP runtime instances* |
| **CI** | GitHub Actions, one job per module, triggered on PR into develop/main |
| **Diagrams** | Mermaid, committed as text in /docs |

---

## 4. Repo Layout

```
ridelink/
├── pom.xml                         (parent — dependency BOM, plugin versions)
├── account-service/
│   ├── pom.xml
│   └── src/main/java/.../account/
├── driver-vehicle-service/
│   ├── pom.xml
│   └── src/main/java/.../drivervehicle/
├── ride-management-service/
│   ├── pom.xml
│   └── src/main/java/.../ride/
├── fare-payment-service/
│   ├── pom.xml
│   └── src/main/java/.../farepayment/
├── docs/
│   ├── architecture.md             (Mermaid component diagram)
│   ├── sequence-ride-booking.md    (Mermaid sequence diagram)
│   ├── contracts/                  (agreed REST + event JSON schemas)
│   ├── decisions/                  (architecture decision records)
│   ├── report/                     (technical report source material)
│   └── testing/                    (test plan and evidence)
├── postman/
│   └── RideLink.postman_collection.json
├── ../.github/workflows/ci.yml     (GitHub requires workflows at repository root)
└── README.md                       (build-root commands; repository README has project guide)
```

## 5. Member Assignment & Communication Matrix

| Member | Service | Depends on (sync) | Publishes / Consumes (async) |
| :--- | :--- | :--- | :--- |
| **1** | Account Service | — | — |
| **2** | Driver & Vehicle Service | — | — |
| **3** | Ride Management Service | calls Driver&Vehicle (eligible drivers), calls Fare&Payment (final fare) | consumes `PaymentRecorded` |
| **4** | Fare & Payment Service | — | publishes `PaymentRecorded` |

### Communication Summary — Corrected Split for G3

**Synchronous (RestClient) — Immediate Response Required:**
- `Ride Management → Driver & Vehicle`: GET eligible available drivers — **sync** because Ride Mgmt must have the list immediately to perform assignment/selection and return to passenger. No point deferring; driver availability is a pre-condition for the ride request.
- `Ride Management → Fare & Payment`: POST final fare calculation — **sync** because Ride Mgmt needs the number back immediately to display it to passenger / driver and store it on the ride record before completion. Blocking call is justified: low latency, small payload, request-response semantics.

**Asynchronous (RabbitMQ / CloudAMQP) — Fire-and-Forget:**
- `PaymentRecorded` event: Fare & Payment → Ride Management — **async** because it's a fire-and-forget confirmation Ride Mgmt only needs to react to, not block on. Payment settlement can take time, may retry, and Ride Mgmt should not hold the HTTP request open waiting for it. Decouples completion from financial confirmation.

> **G3 Justification for Report:** This gives a clean comparison: fare finalization = synchronous REST (needs immediate response, tight coupling acceptable for UX), payment settlement = asynchronous messaging (eventual consistency, resilience to retries, avoids blocking). Compare RestClient vs RabbitMQ: RestClient offers simplicity + immediacy + HTTP status codes, but creates temporal coupling; RabbitMQ offers decoupling + durability + retry via CloudAMQP, but introduces eventual consistency. We chose each where its trade-off is appropriate.

**Auth Flow:**
- Account Service issues JWT (HMAC secret in env var)
- Other 3 services validate same secret via Spring Security filter

---

## 6. Environment Variables & Isolation

```env
# Per service .env (example)
MONGODB_URI=mongodb+srv://<service_user>:<pwd>@ridelink.xxxxx.mongodb.net/<service_db>?retryWrites=true&w=majority
JWT_SECRET=<256-bit HMAC secret — shared across services>
CLOUDAMQP_URL=amqps://<user>:<pwd>@<host>/<vhost>
```

- **Data Isolation:** 4 databases: `account_db`, `driver_vehicle_db`, `ride_mgmt_db`, `fare_payment_db`
- **Credential Isolation:** 4 Atlas DB users, each scoped to only its own DB
- **No secrets in Git:** All via env vars / GitHub Secrets for CI

---

## 7. Compliance Mapping to Brief

| Requirement | How Satisfied |
| :--- | :--- |
| §6.1 Four services, own persistence boundary | 4 Spring Boot services + 4 separate Atlas databases + scoped users |
| §6.2 REST + async, 2+ interservice interactions | RestClient sync (2 calls) + RabbitMQ async (2 events) |
| §6.3 Security, validation, no committed secrets | JWT + RBAC, env vars, validation |
| §6.4 One shared Git repo, branching, CI | Monorepo + GitHub Actions (per-module jobs) |
| §6.5 No frontend, Swagger + Postman | springdoc-openapi per service + Postman collection in `/postman` |
