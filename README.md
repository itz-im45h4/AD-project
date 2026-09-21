# RideLink — Backend Microservices | IT3130 Group Assignment

> **Ride-sharing backend built as 4 independently executable Spring Boot microservices. No frontend — demo via Swagger UI + Postman.**

**Monorepo:** Maven Multi-Module | **One shared Git repo** as required by §6.4  
**Status:** Stack Finalized — Do not change without group approval

---

### ⚠️ MANDATORY COMPLIANCE — READ BEFORE YOU CODE

From `rules_1.txt` — violation = 0 marks:

1.  **All 4 services MUST be Java + Spring Boot.** MERN / Node.js / Express is NOT permitted as replacement.
2.  **Each service MUST have its own database.** No single shared DB across services.
3.  MongoDB is explicitly allowed. We use it for all 4 services with isolation.
4.  Frontend is NOT required. We demonstrate with Swagger UI + Postman.
5.  No secrets in Git. Use env vars / GitHub Secrets.

**If you are thinking of using Node, Express, or a shared DB — STOP and read this README again.**

---

### 1. Finalized Stack

| Layer | Choice — DO NOT CHANGE |
| :--- | :--- |
| **Language / Framework** | Java 21, Spring Boot 3.3.x |
| **Build** | Maven multi-module monorepo (parent POM manages versions) |
| **Database** | MongoDB Atlas — ONE M0 free cluster, **4 separate databases** |
| **ODM** | Spring Data MongoDB |
| **Sync Interservice** | Spring RestClient (Ride Mgmt → Driver & Fare) |
| **Async Messaging** | RabbitMQ via CloudAMQP free tier + Spring AMQP |
| **Auth** | JWT HMAC issued by Account Service, same `JWT_SECRET` validated by other 3 services |
| **API Docs** | springdoc-openapi — Swagger UI auto-generated per service |
| **Testing** | JUnit 5 + Mockito (unit) + Testcontainers (integration — spins local Mongo/Rabbit for tests only) |
| **CI** | GitHub Actions — 1 job per module on PR to `develop`/`main` |
| **Diagrams** | Mermaid in `/docs` |

**Data Isolation:** 4 databases, 4 scoped DB users on same Atlas cluster:
- `account_db` → Account Service
- `driver_vehicle_db` → Driver & Vehicle Service
- `ride_mgmt_db` → Ride Management Service
- `fare_payment_db` → Fare & Payment Service

---

### 2. Repo Layout

```
ridelink/
├── pom.xml                         // parent BOM
├── account-service/                // 8081
├── driver-vehicle-service/         // 8082
├── ride-management-service/        // 8083
├── fare-payment-service/           // 8084
├── docs/
│   ├── architecture.md             // Mermaid component diagram
│   ├── sequence-ride-booking.md    // Mermaid sequence diagram
│   └── contracts/                  // Agreed REST + event JSON schemas
├── postman/
│   └── RideLink.postman_collection.json
├── .github/workflows/ci.yml
└── README.md
```

---

### 3. Member Ownership 

| Member | Service | Owns | Explicitly NOT Your Job |
| :--- | :--- | :--- | :--- |
| **Member 1 — Account & Identity** | `account-service` | Identity, credentials, roles, JWT issuance, profile, status | Vehicles, rides, money |
| **Member 2 — Driver & Vehicle** | `driver-vehicle-service` | Driver profile, vehicle, ONLINE/OFFLINE/BUSY, simulated location, `GET /drivers/available` | Deciding who gets assigned |
| **Member 3 — Ride Management** | `ride-management-service` | Ride lifecycle state machine, assignment rule, orchestration | Calculating fare, payment |
| **Member 4 — Fare & Payment** | `fare-payment-service` | Estimation, final fare, simulated payment, receipt, publishes `PaymentRecorded` | Ride status transitions |

**Overlap Contract for Report (copy this line):**
> *Driver&Vehicle answers "who's available", Ride Management decides "who's assigned". Fare&Payment answers "how much", Ride Management owns "what's the ride state".*

Detailed responsibilities: see `/docs/Member-Responsibilities.md`

---

### 4. Service Ports & Swagger

| Service | Port | Swagger UI |
| :--- | :--- | :--- |
| Account | 8081 | http://localhost:8081/swagger-ui.html |
| Driver & Vehicle | 8082 | http://localhost:8082/swagger-ui.html |
| Ride Management | 8083 | http://localhost:8083/swagger-ui.html |
| Fare & Payment | 8084 | http://localhost:8084/swagger-ui.html |

---

### 5. Communication Design — For G3 Marks

**Sync (RestClient) — Immediate response required:**
1. `Ride Mgmt → Driver&Vehicle: GET /drivers/available?zone=X` — needs list now to assign
2. `Ride Mgmt → Fare&Payment: POST /fares/finalize` — needs number now to display/store

**Async (RabbitMQ / CloudAMQP) — Fire-and-forget:**
- `Fare&Payment --PaymentRecorded--> Ride Mgmt` — payment settlement can retry, should not block ride completion HTTP request.

**Justification for report:** Sync = simplicity + immediacy + HTTP codes but temporal coupling. Async = decoupling + durability + retries via CloudAMQP but eventual consistency. Chose each where trade-off fits.

**Auth Flow:** Account issues JWT → Other 3 services validate same HMAC secret via Spring Security filter.

---

### 6. Environment Variables — (yet to begin will update in future) 

Create `.env` in each service root (NEVER commit):

```env
# Example — each service uses its own scoped user + its own DB
MONGODB_URI=mongodb+srv://account_user:PWD@ridelink.xxxxx.mongodb.net/account_db?retryWrites=true&w=majority
JWT_SECRET=replace-with-256-bit-base64-secret-shared-across-all-4-services
CLOUDAMQP_URL=amqps://user:pass@host/vhost
```

Generate secret: `openssl rand -base64 32`

**Isolation:**
- 4 Atlas DB users → each only can read/write its own DB
- One cluster, 4 databases — satisfies §6.1 persistence boundary
- All secrets via env vars + GitHub Secrets for CI

---

### 7. Quick Start (yet to begin will update in future) 

**Prereqs:** Java 21, Maven 3.9+, Git, Docker Desktop (for Testcontainers tests only)

```bash
# 1. Clone
git clone <your-repo-url>
cd ridelink

# 2. Set env vars in each service (or IDE run config)
# 3. Build all
mvn clean install

# 4. Run each service in separate terminal (order matters for first run)
cd account-service && mvn spring-boot:run
cd driver-vehicle-service && mvn spring-boot:run
cd fare-payment-service && mvn spring-boot:run
cd ride-management-service && mvn spring-boot:run

# 5. Test
mvn test
```

Atlas + CloudAMQP means no local Mongo/Rabbit to run for dev — only Docker needed for tests.

---

### 8. Git Workflow — For G4 + I4 Marks

```
main (protected, always demonstrable)
└── develop
    ├── feature/account-service
    ├── feature/driver-vehicle-service
    ├── feature/ride-management-service
    └── feature/fare-payment-service
```

- No direct push to `main` or `develop`
- Feature branch → PR → 1 peer review → merge
- No bulk commit on deadline — commit history is graded
- CI runs `mvn verify` for all 4 modules on every PR

---

### 9. Testing & Demo

**Unit:** JUnit 5 + Mockito per service  
**Integration:** Testcontainers (local containers for tests, separate from Atlas/CloudAMQP)  
**Official Demo:** Swagger UI per service + `/postman/RideLink.postman_collection.json` (happy path + 2 negative scenarios: NoDriverAvailable, PaymentFailed)

**State Machine for Report:**
`REQUESTED → ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED` + `CANCELLED` branch

**Fare Formula (document in report):**
```
estimated = base_fare(200) + distance_km*per_km_rate + duration_min*per_min_rate
final = estimated + surge_multiplier - discount
```

---

### 10. How NOT to Break The Project

- ❌ Don't use Node.js / Express / MERN — 0 marks
- ❌ Don't share a DB or query another service's DB directly
- ❌ Don't embed user/driver objects — store only `userId`, `rideId`, `driverId` as ID references
- ❌ Don't commit `.env` or `JWT_SECRET` or Atlas passwords
- ❌ Don't add an API Gateway and count it as one of the 4 core services — Gateway does not count per brief

---

**Questions?** Check instructions folder before changing anything.

