# REST DTO Contract (v1)

All paths below are relative to `/api/v1`. Fields marked required must be validated by the owning service. Responses may add service-owned fields but must not rename or remove documented fields without a versioned contract update.

## Account Service

| Operation | Request | Response |
| --- | --- | --- |
| `POST /accounts/register` | `name`, `email`, `phone`, `password`, `role` (`PASSENGER` or `DRIVER`) | `id`, `name`, `email`, `phone`, `role`, `status`, `createdAt` |
| `POST /auth/login` | `email`, `password` | `accessToken`, `tokenType` (`Bearer`), `expiresAt`, `user` (`id`, `role`, `status`) |
| `GET /accounts/me` | — | account response without password |
| `PUT /accounts/me` | `name`, `email`, `phone` | updated account response |
| `PATCH /accounts/{accountId}/status` | `status` (`ACTIVE`, `SUSPENDED`, `DEACTIVATED`) | updated account response |

## Driver & Vehicle Service

| Operation | Request | Response |
| --- | --- | --- |
| `POST /drivers/profile` | `userId`, `licenseNumber`, `vehicle` (`make`, `model`, `plateNumber`, `capacity`), `serviceZone` | `id`, `userId`, `availability`, `serviceZone`, `vehicle`, `createdAt` |
| `PUT /drivers/{driverId}/availability` | `availability` (`ONLINE`, `OFFLINE`, `ON_TRIP`) | updated driver profile |
| `PUT /drivers/{driverId}/location` | `latitude`, `longitude`, `zone` | updated driver profile |
| `GET /drivers/available?zone={zone}` | — | array of `id`, `userId`, `serviceZone`, `latitude`, `longitude`, `vehicle` |

## Ride Management Service

| Operation | Request | Response |
| --- | --- | --- |
| `POST /rides` | `passengerId`, `pickup` (`address`, `zone`, `latitude`, `longitude`), `destination` (same shape) | `id`, `passengerId`, `status`, `pickup`, `destination`, `createdAt` |
| `POST /rides/{rideId}/assign` | optional `driverId`; absent means select the first returned eligible driver | ride response with `driverId` and `ASSIGNED` status |
| `POST /rides/{rideId}/accept` | — | updated ride response |
| `POST /rides/{rideId}/start` | — | updated ride response |
| `POST /rides/{rideId}/complete` | `distanceKm`, `durationMinutes` | completed ride response with `finalFare` |
| `POST /rides/{rideId}/cancel` | optional `reason` | cancelled ride response |
| `GET /rides?userId={userId}` | — | array of ride responses |

The ride state machine is `REQUESTED → ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED`, with cancellation permitted from `REQUESTED`, `ASSIGNED`, and `ACCEPTED`.

## Fare & Payment Service

| Operation | Request | Response |
| --- | --- | --- |
| `POST /fares/estimate` | `pickup`, `destination`, `distanceKm`, `durationMinutes` | `estimatedFare`, `currency`, `baseFare`, `perKmRate`, `perMinuteRate` |
| `POST /fares/finalize` | `rideId`, `distanceKm`, `durationMinutes` | `fareId`, `rideId`, `finalFare`, `currency`, `status` |
| `POST /payments` | `rideId`, `fareId`, `method` | `paymentId`, `rideId`, `fareId`, `status`, `amount`, `currency`, `recordedAt` |
| `GET /receipts/{rideId}` | — | `receiptId`, `rideId`, `paymentId`, `amount`, `currency`, `status`, `issuedAt` |

The fixed initial currency is `LKR`. Fare parameters and simulated failure behavior are service-owned implementation details, but their externally returned amount and payment status must follow this contract.
