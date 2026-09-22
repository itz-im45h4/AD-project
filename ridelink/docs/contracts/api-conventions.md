# API Conventions (v1)

- Base path: `/api/v1`.
- JSON properties use `camelCase`.
- Public and cross-service identifiers are opaque UUID strings; MongoDB object IDs are not API contracts.
- All timestamps are ISO-8601 UTC strings, for example `2026-09-22T10:15:30Z`.
- Validation, authorization, and business errors use `application/problem+json` following RFC 9457, with `type`, `title`, `status`, `detail`, `instance`, and optional `errors` fields.
- Successful create operations return `201 Created` with the created resource. State actions return the updated resource. Missing resources return `404`; invalid state transitions return `409`; invalid input returns `400`.
- JWT bearer authentication is required for protected operations. Account Service issues tokens; the other services validate the same configured HMAC secret.

## Service Boundaries

| Service | Initial public operations |
| --- | --- |
| Account | `POST /accounts/register`, `POST /auth/login`, `GET/PUT /accounts/me`, `PATCH /accounts/{id}/status` |
| Driver & Vehicle | `POST /drivers/profile`, `PUT /drivers/{id}/availability`, `PUT /drivers/{id}/location`, `GET /drivers/available?zone={zone}` |
| Ride Management | `POST /rides`, state actions at `/rides/{rideId}`, `GET /rides?userId={userId}` |
| Fare & Payment | `POST /fares/estimate`, `POST /fares/finalize`, `POST /payments`, `GET /receipts/{rideId}` |

The request and response field contract is in [`rest-api.v1.md`](rest-api.v1.md). No service may change a documented cross-service request, response, or event without team agreement and a versioned contract update.
