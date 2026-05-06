# Member 2 — Submission Document
## On-Demand Home Services Marketplace | DS Assignment 2

---

## 1. Responsibilities

Member 2 owns:
- **Offer Service** (port 8083) — manages service listings and categories
- **Booking Service** (port 8084) — orchestrates the full booking flow

---

## 2. Services Overview

### Offer Service (port 8083)
Spring Boot service with its own H2 in-memory database (`offerdb`).

**What it does:**
- Providers post, browse, and update service listings
- Admin registers new service categories
- Booking Service calls it to validate a listing is OPEN and to mark it BOOKED after payment
- Team-of-3: matches customer requests to available listings and publishes a RabbitMQ alert to the matched provider

**Key entities:**
- `ServiceListing` (mapped to `service_listings` table)
- `ServiceCategory` (mapped to `service_categories` table)

### Booking Service (port 8084)
Spring Boot service with its own H2 in-memory database (`bookingdb`).

**What it does:**
- Receives a booking request `{ customerId, offerId }`
- Internally orchestrates: validate offer → check wallet → deduct wallet → mark offer BOOKED → publish RabbitMQ events
- Publishes `BookingConfirmed` to `bookings.exchange` (fanout) on success
- Publishes `PaymentFailed` to `payments.exchange` (direct, key: `payment.failed`) on failure
- Exposes booking history by customer, by provider, and all (for Admin)

**Key entity:** `Reservation` (mapped to `reservations` table)

---

## 3. Design Decisions & Assumptions

### No Eureka / Service Discovery
The assignment labs showed Eureka as optional tooling. Our team decided to skip it and use direct HTTP calls between services (configured in `application.properties`). This keeps each service simpler and avoids an extra process to run.

### H2 In-Memory Database
Each service uses H2 in-memory. This satisfies the microservice requirement of "each service has its own DB" without needing MySQL/PostgreSQL installed. On restart, data is cleared — this is expected for a demo/assignment setup.

### Wallet Service URL
The Wallet Service (M1) runs on WildFly at a different context path:
- Balance: `http://localhost:8082/wallet-service/api/wallet/{userId}/balance`
- Deduct: `http://localhost:8082/wallet-service/api/wallet/{userId}/deduct`
- Rollback: `http://localhost:8082/wallet-service/api/wallet/{userId}/rollback`

These URLs are hardcoded in `booking-service/src/main/resources/application.properties`. If M1 changes their port or context path, update the `services.wallet-service.base-url` property.

### RabbitMQ Exchange Names
Both services use the exchange names agreed in the API contract:
- `bookings.exchange` — fanout, published by Booking Service on success
- `payments.exchange` — direct, routing key `payment.failed`, published on failure

The Booking Service declares all queues (`booking.confirmation.queue`, `notification.queue`, `customer.notification.queue`, `admin.notification.queue`) so they exist regardless of which service starts first.

### Offer Status Naming
M1 used `ACTIVE`/`COMPLETED` in early drafts. We aligned to `OPEN`/`BOOKED` as defined in the final API contract, since these map more clearly to the lifecycle.

### Category Seeding
Five categories are seeded on startup via `CategorySeeder`: Plumbing, Carpentry, Electrical, Cleaning, Painting. These match the assignment example. Additional categories can be added at runtime via `POST /api/offers/categories`.

### Booking Rollback Flow
If the offer mark-as-BOOKED call fails after the wallet deduction, the service:
1. Calls `PATCH /wallet/{userId}/rollback` to refund the customer
2. Deletes the draft `Reservation` record
3. Publishes `PAYMENT_FAILED` to notify the customer

This ensures no money is lost and the system stays consistent without distributed transactions.

---

## 4. How to Run

### Prerequisites
| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| Docker | any recent |

### Step 1 — Start RabbitMQ
```bash
docker run -it --rm --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:4.0-management
```
Verify at http://localhost:15672 (user: guest / pass: guest)

### Step 2 — Start M1 services (prerequisite)
M1 must start User Service (port 8081) and Wallet Service (port 8082) first.
Customers must be registered and wallets created before placing bookings.

### Step 3 — Start Offer Service
```bash
cd offer-service
mvn spring-boot:run
```
Verify at: http://localhost:8083/api/offers/categories  
You should see the 5 seeded categories.

H2 console: http://localhost:8083/h2-console  
JDBC URL: `jdbc:h2:mem:offerdb`

### Step 4 — Start Booking Service
```bash
cd booking-service
mvn spring-boot:run
```
Verify at: http://localhost:8084/api/bookings/all  
Should return an empty array `[]`.

H2 console: http://localhost:8084/h2-console  
JDBC URL: `jdbc:h2:mem:bookingdb`

### Step 5 — Import Postman Collection
Import `Member2_Offer_Booking.postman_collection.json` into Postman.
The collection has two base URL variables pre-set.

---

## 5. Test Scenario (Happy Path)

1. **Register a provider** via User Service (M1):  
   `POST http://localhost:8081/user-service/api/users/register`  
   `{ "username": "hassan_p", "password": "pass", "role": "PROVIDER", "profession": "Plumber" }`

2. **Register a customer** (also via M1) with initial balance $200.

3. **Create a listing** — use the provider's userId:  
   `POST http://localhost:8083/api/offers`  
   `{ "providerId": 2, "categoryId": 1, "price": 80.00, "availableDate": "2026-06-15T09:00:00" }`

4. **Browse listings by category**:  
   `GET http://localhost:8083/api/offers?categoryId=1`

5. **Place a booking** — use the customer's userId and the listing's offerId:  
   `POST http://localhost:8084/api/bookings`  
   `{ "customerId": 1, "offerId": 1 }`

   Expected: 201 with booking record. Wallet deducted ($200 → $120). Listing status → BOOKED. RabbitMQ events fired.

6. **View booking history**:  
   `GET http://localhost:8084/api/bookings?customerId=1`

7. **Provider sees completed service**:  
   `GET http://localhost:8083/api/offers/provider/2/completed`

---

## 6. RabbitMQ Events Published

| Exchange | Type | Routing Key | Event | When |
|----------|------|-------------|-------|------|
| `bookings.exchange` | fanout | (empty) | `BOOKING_CONFIRMED` | Booking succeeds |
| `payments.exchange` | direct | `payment.failed` | `PAYMENT_FAILED` | Insufficient balance / offer unavailable |
| `bookings.exchange` | fanout | (empty) | `SERVICE_REQUEST_MATCHED` | Customer request matched to a provider |

---

## 7. API Reference (Quick)

### Offer Service — http://localhost:8083
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/offers/categories` | List all categories |
| POST | `/api/offers/categories` | Add category (admin) |
| POST | `/api/offers` | Create listing (provider) |
| GET | `/api/offers` | Browse OPEN listings |
| GET | `/api/offers?categoryId={id}` | Filter by category |
| GET | `/api/offers/{offerId}` | Single listing |
| PATCH | `/api/offers/{offerId}` | Update price/date |
| PATCH | `/api/offers/{offerId}/complete` | Mark BOOKED (Booking Svc) |
| GET | `/api/offers/provider/{id}` | Provider's all listings |
| GET | `/api/offers/provider/{id}/completed` | Provider's completed services |
| POST | `/api/offers/match` | Match customer request |
| POST | `/api/offers/{offerId}/accept` | Provider accepts request |

### Booking Service — http://localhost:8084
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/bookings` | Place a booking |
| GET | `/api/bookings?customerId={id}` | Customer history |
| GET | `/api/bookings?providerId={id}` | Provider history |
| GET | `/api/bookings/all` | All bookings (admin) |
| GET | `/api/bookings/{bookingId}` | Single booking |
