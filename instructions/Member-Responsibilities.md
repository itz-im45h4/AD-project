# RideLink — Member Responsibilities & Service Boundaries

> IT3130 - Application Development | 4-Member Group | Monorepo: Maven Multi-Module

This document defines **primary ownership**, **minimum responsibility**, and **explicit non-goals** for each service to satisfy §6.1 (data ownership), §7 (individual accountability), and to avoid overlap disputes in the report.

---

## Member 1 — Account & Identity Service

**Owns:** user identity, credentials, roles, account lifecycle. Nothing about what a driver does on the road or what a ride costs — only **who someone is and what they're allowed to do.**

| Responsibility | Detail |
| :--- | :--- |
| **Registration** | Passenger and driver sign-up (separate role flag, shared User collection or role-discriminated documents) |
| **Authentication** | Login endpoint, JWT issuance (access token with `role` claim) |
| **Authorization support** | Exposes the shared JWT secret/verification contract other services validate against (HMAC secret in env var) |
| **Profile management** | View/update profile (name, contact, email) |
| **Account status** | Activate / suspend / deactivate account |

> **Explicitly NOT this service's job:** vehicle details, driver availability, ride history. Account only stores identity — Ride Management stores the historical link between a `userId` and a `Ride` by ID reference, not by embedding account data.

**DB:** `account_db` — `users` collection  
**Endpoints:** `POST /accounts/register`, `POST /auth/login`, `GET /accounts/me`, `PUT /accounts/me`, `PATCH /accounts/{id}/status`

---

## Member 2 — Driver & Vehicle Operations Service

**Owns:** everything about a driver's operational readiness to accept a ride. Nothing about the ride itself once it exists.

| Responsibility | Detail |
| :--- | :--- |
| **Driver operational profile** | License info, rating, vehicle assignment (references `userId` from Account, doesn't duplicate identity data) |
| **Vehicle details** | Make / model / plate / capacity |
| **Availability** | Online / offline / on-trip status |
| **Simulated location** | Current coordinates or service-area zone, updatable via endpoint |
| **Eligible-driver query** | `GET /drivers/available?zone=X` — this is the endpoint Ride Management calls **synchronously** |

> **Explicitly NOT this service's job:** deciding which driver gets assigned to a ride. That decision logic lives in **Ride Management**, which merely queries this service for candidates.
>
> **Report sentence to include:** *Driver&Vehicle answers "who's available," Ride Management decides "who's assigned."* — this is the single most likely overlap point, so state it plainly in the report.

**DB:** `driver_vehicle_db` — `drivers`, `vehicles` collections  
**Endpoints:** `POST /drivers/profile`, `PUT /drivers/{id}/availability`, `PUT /drivers/{id}/location`, `GET /drivers/available`

---

## Member 3 — Ride Management Service

**Owns:** the ride lifecycle state machine, end to end, and orchestration of the booking workflow. This is the **orchestrator**.

| Responsibility | Detail |
| :--- | :--- |
| **Ride request creation** | Pickup / destination, passenger `userId` |
| **Driver assignment** | Calls Driver&Vehicle for candidates (`GET /drivers/available`), applies assignment rule (e.g., nearest / first-available — document the rule, it doesn't need to be sophisticated) |
| **Status lifecycle** | `REQUESTED → ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED`, plus `CANCELLED` branch, with an explicit transition-validity table |
| **Ride retrieval** | History per user, ride detail by ID |
| **Completion trigger** | **Sync:** calls `POST /fares/finalize` to get final fare immediately (needs number back to display). **Async:** consumes `PaymentRecorded` to close the record |

> **Explicitly NOT this service's job:** calculating fares or handling payment — it triggers Fare&Payment and reacts to its outcome, but owns no money logic. This is the orchestrator, not the fare authority.

**DB:** `ride_mgmt_db` — `rides` collection  
**Endpoints:** `POST /rides`, `POST /rides/{id}/assign`, `POST /rides/{id}/accept`, `POST /rides/{id}/start`, `POST /rides/{id}/complete`, `POST /rides/{id}/cancel`, `GET /rides?userId=X`  
**Sync Calls:** `Driver&Vehicle (eligible drivers)`, `Fare&Payment (final fare)` — sync because immediate response required  
**Consumes:** `PaymentRecorded` — async fire-and-forget

**State Transition Table (to put in report):**

| From | Allowed To | Triggered By |
| :--- | :--- | :--- |
| REQUESTED | ASSIGNED, CANCELLED | assign endpoint / cancel endpoint |
| ASSIGNED | ACCEPTED, CANCELLED | driver accept / passenger cancel |
| ACCEPTED | IN_PROGRESS, CANCELLED | driver start |
| IN_PROGRESS | COMPLETED | driver complete + fare finalization sync call |
| COMPLETED | — | waits for PaymentRecorded async to mark payment settled |

---

## Member 4 — Fare & Payment Service

**Owns:** everything money-related — estimation, final calculation, simulated settlement, receipts.

| Responsibility | Detail |
| :--- | :--- |
| **Fare estimation** | `POST /fares/estimate` given pickup/destination (distance × rate + base fare — document the formula) |
| **Final fare calculation** | Triggered **synchronously** by Ride Management at trip completion — recommend: Ride Mgmt calls `POST /fares/finalize` synchronously since it needs the fare before it can consider the ride truly closed, then Fare&Payment separately publishes `PaymentRecorded` once settlement completes |
| **Simulated payment** | Record payment method, status (`PENDING/SUCCESS/FAILED`) — no real payment provider |
| **Receipt** | Generate/retrieve receipt tied to `rideId` |

> **Explicitly NOT this service's job:** knowing anything about ride status transitions beyond receiving the completion signal and reporting back — it doesn't mutate Ride state directly, ever (no cross-service DB writes, per §6.1).

**DB:** `fare_payment_db` — `fares`, `payments`, `receipts` collections  
**Endpoints:** `POST /fares/estimate`, `POST /fares/finalize`, `POST /payments`, `GET /receipts/{rideId}`  
**Publishes:** `PaymentRecorded` — async, because settlement is fire-and-forget; Ride Mgmt only needs to react, not block

**Formula Example (document in report):**
```
estimated_fare = base_fare + (distance_km * per_km_rate) + (duration_min * per_min_rate)
final_fare = estimated_fare + surge_multiplier (if applicable) - discount
```

---

## Cross-Cutting Rules (for G1 + G4 marks)

1.  **No cross-DB access:** One Atlas cluster, 4 databases, 4 scoped DB users. Service A never queries Service B's DB.
2.  **ID reference only:** Services store `userId`, `rideId`, `driverId` as IDs, not embedded documents.
3.  **Overlap contract:** Driver&Vehicle = "who's available" → Ride Mgmt = "who's assigned". Fare&Payment = "how much" → Ride Mgmt = "what's the ride state".
4.  **Sync vs Async — G3 justification:**
    - Fare finalization = **Sync** — Ride Mgmt needs number immediately to display.
    - Payment settlement = **Async** — fire-and-forget confirmation, avoids blocking, allows retries.
5.  **Viva readiness:** Each member must be able to run the whole monorepo (`mvn spring-boot:run` per service) and explain the other 3 services at high level.

---

## Deliverable Checklist per Member (I1-I3)

- [ ] Own service implements all responsibilities above + validation + error handling
- [ ] Unit tests (JUnit 5 + Mockito) + integration test (Testcontainers for Mongo/RabbitMQ)
- [ ] OpenAPI via springdoc-openapi (Swagger UI auto)
- [ ] Meaningful commits, feature branches, PRs, reviews — no bulk commit on deadline
- [ ] Section in technical report + README owner table
