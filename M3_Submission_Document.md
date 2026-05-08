# Member 3 — Submission Document
## On-Demand Home Services Marketplace | DS Assignment 2

---

## 1. Responsibilities

Member 3 owns:
- **Admin Service** (port 8085) — admin operations aggregator
- **Notification Service** (port 8086) — RabbitMQ consumer and notification storage
- **API Gateway** (port 8080) — single entry point for all services

---

## 2. Services Overview

### Admin Service (port 8085)
Spring Boot service with **NO database** — aggregates data from peer services via REST.

**What it does:**
- View all registered users (delegates to User Service)
- View all transaction/booking history (delegates to Booking Service)
- List all service categories (delegates to Offer Service)
- Add new service categories (delegates to Offer Service)
- Receive payment failure alerts via RabbitMQ (team-of-3 feature)

**RabbitMQ Consumer:**
- Queue: `admin.notification.queue`
- Exchange: `payments.exchange` (direct)
- Routing key: `payment.failed`
- Stores alerts in-memory, exposed via `GET /api/admin/alerts`

**Key endpoints:**
- `GET  /api/admin/users` — all registered users
- `GET  /api/admin/bookings` — all booking records
- `GET  /api/admin/categories` — all service categories
- `POST /api/admin/categories` — add new category
- `GET  /api/admin/alerts` — payment failure alerts (RabbitMQ)

### Notification Service (port 8086)
Spring Boot service with its own MySQL database (`notificationdb`).

**What it does:**
- Consumes RabbitMQ events from three queues
- Persists notifications to database
- Exposes REST endpoint to retrieve notifications per user (JSON format)

**RabbitMQ Consumers:**

| Queue | Exchange | Routing Key | Event Types |
|-------|----------|-------------|-------------|
| `notification.queue` | `bookings.exchange` (fanout) | "" | `BOOKING_CONFIRMED`, `SERVICE_REQUEST_MATCHED` |
| `customer.notification.queue` | `payments.exchange` (direct) | `payment.failed` | `PAYMENT_FAILED` |
| `provider.match.queue` | `bookings.exchange` (fanout) | "" | `SERVICE_REQUEST_MATCHED` |

**Event handling:**
- `BOOKING_CONFIRMED` → creates 2 notifications (customer + provider)
- `PAYMENT_FAILED` → creates 1 notification (customer)
- `SERVICE_REQUEST_MATCHED` → creates 2 notifications (customer + provider)

**Key entity:** `Notification` (table: `notifications`)
- Fields: id, userId, type, message, payload (JSON), createdAt

**Key endpoints:**
- `GET /api/notifications/{userId}` — all notifications for a user (JSON)
- `GET /api/notifications` — all notifications (admin view)
- `GET /api/notifications/health` — health check

### API Gateway (port 8080)
Spring Cloud Gateway — single entry point for all microservices.

**What it does:**
- Routes all incoming requests to the appropriate backend service
- Handles path rewriting for JBoss WAR context paths
- Enables CORS for Postman/browser clients
- Provides centralized health monitoring

**Routing table:**

| Gateway Path | Backend Service | Backend URL |
|--------------|-----------------|-------------|
| `/users/**` | User Service | `http://localhost:8081/user-service/api/users/**` |
| `/wallet/**` | Wallet Service | `http://localhost:8082/wallet-service/api/wallet/**` |
| `/offers/**` | Offer Service | `http://localhost:8083/api/offers/**` |
| `/bookings/**` | Booking Service | `http://localhost:8084/api/bookings/**` |
| `/admin/**` | Admin Service | `http://localhost:8085/api/admin/**` |
| `/notifications/**` | Notification Service | `http://localhost:8086/api/notifications/**` |

**Health endpoint:** `GET http://localhost:8080/actuator/health`

---

## 3. Design Decisions & Assumptions

### Admin Service — No Database
Admin Service is a pure aggregator with no persistent state. It delegates all data queries to the owning services (User, Booking, Offer). Payment failure alerts are stored in-memory since they're transient operational data.

### Notification Service — MySQL Database
Notifications are persisted to enable historical queries and audit trails. Each RabbitMQ event creates one or more notification records addressed to specific users (customerId, providerId, or 0 for admin).

### API Gateway — Path Rewriting
The gateway handles the complexity of different context paths:
- JBoss WAR services (user-service, wallet-service) have `/service-name/api` prefixes
- Spring Boot services (offer, booking, admin, notification) have just `/api` prefixes
- Gateway normalizes all paths to `/resource/**` format for clients

### RabbitMQ Queue Declarations
All queues and exchanges are declared idempotently by multiple services. This allows any service to start first without dependency ordering. Spring AMQP's declarative configuration ensures queues exist before consumers start.

### No JWT / Authentication
Consistent with M1 and M2, no JWT or token-based auth is implemented. The gateway routes all requests without validation. For production, a JWT filter would be added to the gateway to validate tokens before proxying.

### Database Password
Notification Service uses the same MySQL password as M2 services (`Raghad@20226041`). If your MySQL root password differs, update `notification-service/src/main/resources/application.properties`.

---

## 4. How to Run

### Prerequisites
| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Docker | any recent (for RabbitMQ) |

### Step 1 — Start RabbitMQ
```bash
docker run -it --rm --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:4.0-management
```
Verify at http://localhost:15672 (user: guest / pass: guest)

### Step 2 — Start M1 services (prerequisite)
M1 must start User Service (port 8081) and Wallet Service (port 8082) first.

### Step 3 — Start M2 services (prerequisite)
M2 must start Offer Service (port 8083) and Booking Service (port 8084).

### Step 4 — Start Admin Service
```bash
cd admin-service
mvn spring-boot:run
```
Verify at: http://localhost:8085/api/admin/users  
Should return an array of users from User Service.

### Step 5 — Start Notification Service
```bash
cd notification-service
mvn spring-boot:run
```
Verify at: http://localhost:8086/api/notifications/health  
Should return `{ "service": "notification-service", "status": "UP" }`

MySQL database `notificationdb` will be created automatically.

### Step 6 — Start API Gateway
```bash
cd api-gateway
mvn spring-boot:run
```
Verify at: http://localhost:8080/actuator/health  
Should return `{ "status": "UP" }`

### Step 7 — Import Postman Collection
Import `Member3_Admin_Notification_Gateway.postman_collection.json` into Postman.

The collection has three folders:
1. **Admin Service (Direct)** — calls admin-service directly on port 8085
2. **Notification Service (Direct)** — calls notification-service directly on port 8086
3. **API Gateway (All Services)** — calls all services through the gateway on port 8080

---

## 5. Test Scenario (End-to-End)

### Scenario: Customer books a service, receives notification

1. **Register a provider** (via Gateway):  
   `POST http://localhost:8080/users/register`  
   ```json
   {
     "username": "plumber_ali",
     "password": "pass",
     "role": "PROVIDER",
     "profession": "Plumber"
   }
   ```
   Note the returned `userId` (e.g., 2)

2. **Register a customer** (via Gateway):  
   `POST http://localhost:8080/users/register`  
   ```json
   {
     "username": "customer_sara",
     "password": "pass",
     "role": "CUSTOMER",
     "initialBalance": 500.0
   }
   ```
   Note the returned `userId` (e.g., 1)

3. **Provider creates a listing** (via Gateway):  
   `POST http://localhost:8080/offers`  
   ```json
   {
     "providerId": 2,
     "categoryId": 1,
     "price": 120.0,
     "availableDate": "2026-06-20T10:00:00",
     "notes": "Expert plumbing"
   }
   ```
   Note the returned `offerId` (e.g., 1)

4. **Customer places a booking** (via Gateway):  
   `POST http://localhost:8080/bookings`  
   ```json
   {
     "customerId": 1,
     "offerId": 1
   }
   ```
   Expected: 201 Created with booking record

5. **Check customer notifications** (via Gateway):  
   `GET http://localhost:8080/notifications/1`  
   Expected: Array with one notification:
   ```json
   [
     {
       "id": 1,
       "userId": 1,
       "type": "BOOKING_CONFIRMED",
       "message": "Your booking #1 has been confirmed! Amount charged: 120.0.",
       "payload": "{...}",
       "createdAt": "2026-05-07T..."
     }
   ]
   ```

6. **Check provider notifications** (via Gateway):  
   `GET http://localhost:8080/notifications/2`  
   Expected: Array with one notification:
   ```json
   [
     {
       "id": 2,
       "userId": 2,
       "type": "BOOKING_CONFIRMED",
       "message": "You have a new confirmed booking #1! Customer ID: 1, Amount: 120.0.",
       "payload": "{...}",
       "createdAt": "2026-05-07T..."
     }
   ]
   ```

7. **Admin views all bookings** (via Gateway):  
   `GET http://localhost:8080/admin/bookings`  
   Expected: Array with the booking record

8. **Admin views all users** (via Gateway):  
   `GET http://localhost:8080/admin/users`  
   Expected: Array with both users (customer + provider)

### Scenario: Payment failure triggers admin alert

1. **Customer attempts booking with insufficient balance**:  
   Reduce customer wallet to $10 (via wallet-service deduct endpoint)

2. **Attempt booking** (via Gateway):  
   `POST http://localhost:8080/bookings`  
   ```json
   {
     "customerId": 1,
     "offerId": 2
   }
   ```
   Expected: 402 Payment Required

3. **Check admin alerts** (via Gateway):  
   `GET http://localhost:8080/admin/alerts`  
   Expected: Array with payment failure event:
   ```json
   [
     {
       "eventType": "PAYMENT_FAILED",
       "customerId": 1,
       "offerId": 2,
       "attemptedAmount": 120.0,
       "reason": "INSUFFICIENT_BALANCE",
       "attemptedAt": "2026-05-07T..."
     }
   ]
   ```

4. **Check customer notifications** (via Gateway):  
   `GET http://localhost:8080/notifications/1`  
   Expected: Array includes a PAYMENT_FAILED notification

---

## 6. RabbitMQ Events Consumed

### Admin Service

| Exchange | Type | Routing Key | Event | Action |
|----------|------|-------------|-------|--------|
| `payments.exchange` | direct | `payment.failed` | `PAYMENT_FAILED` | Store in-memory alert |

### Notification Service

| Queue | Exchange | Routing Key | Event | Action |
|-------|----------|-------------|-------|--------|
| `notification.queue` | `bookings.exchange` (fanout) | "" | `BOOKING_CONFIRMED` | Create 2 notifications (customer + provider) |
| `notification.queue` | `bookings.exchange` (fanout) | "" | `SERVICE_REQUEST_MATCHED` | Create 1 notification (customer) |
| `customer.notification.queue` | `payments.exchange` (direct) | `payment.failed` | `PAYMENT_FAILED` | Create 1 notification (customer) |
| `provider.match.queue` | `bookings.exchange` (fanout) | "" | `SERVICE_REQUEST_MATCHED` | Create 1 notification (provider) |

---

## 7. API Reference (Quick)

### Admin Service — http://localhost:8085

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/users` | View all registered users |
| GET | `/api/admin/bookings` | View all booking records |
| GET | `/api/admin/categories` | List all service categories |
| POST | `/api/admin/categories` | Add new category `{ "name": "..." }` |
| GET | `/api/admin/alerts` | View payment failure alerts (RabbitMQ) |

### Notification Service — http://localhost:8086

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/notifications/{userId}` | All notifications for a user (JSON) |
| GET | `/api/notifications` | All notifications (admin view) |
| GET | `/api/notifications/health` | Health check |

### API Gateway — http://localhost:8080

All service endpoints are accessible through the gateway by replacing the service-specific port with `8080` and using the normalized path:

**Examples:**
- Direct: `http://localhost:8081/user-service/api/users/register`  
  Gateway: `http://localhost:8080/users/register`

- Direct: `http://localhost:8084/api/bookings`  
  Gateway: `http://localhost:8080/bookings`

- Direct: `http://localhost:8085/api/admin/users`  
  Gateway: `http://localhost:8080/admin/users`

- Direct: `http://localhost:8086/api/notifications/1`  
  Gateway: `http://localhost:8080/notifications/1`

---

## 8. Team-of-3 Features Implemented

### 1. Service Request Matching (M2 + M3)
- Customer posts service request via User Service
- Offer Service matches request to available listing
- Publishes `SERVICE_REQUEST_MATCHED` to RabbitMQ
- **Notification Service** creates notifications for both customer and provider
- Provider accepts via `POST /api/offers/{offerId}/accept`
- Triggers full booking flow

### 2. Admin Payment Failure Alerts (M3)
- Booking Service publishes `PAYMENT_FAILED` to `payments.exchange` (direct)
- Routing key `payment.failed` routes to `admin.notification.queue`
- **Admin Service** consumes and stores alerts in-memory
- Exposed via `GET /api/admin/alerts`

### 3. Asynchronous Notifications (M3)
- All booking events trigger RabbitMQ messages
- **Notification Service** consumes from 3 queues
- Persists notifications to MySQL
- REST endpoint returns JSON: `GET /api/notifications/{userId}`

---

## 9. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      API Gateway (8080)                         │
│                   Spring Cloud Gateway                          │
└────────┬────────────────────────────────────────────────────────┘
         │
         ├─────────────────────────────────────────────────────────┐
         │                                                         │
    ┌────▼─────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────▼─────┐
    │  User    │  │  Wallet  │  │  Offer   │  │ Booking  │  │  Admin   │
    │ Service  │  │ Service  │  │ Service  │  │ Service  │  │ Service  │
    │  (8081)  │  │  (8082)  │  │  (8083)  │  │  (8084)  │  │  (8085)  │
    │ JBoss EJB│  │ JBoss EJB│  │  Spring  │  │  Spring  │  │  Spring  │
    └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
         │             │              │             │             │
         │             │              │             │             │
    ┌────▼─────┐  ┌───▼──────┐  ┌───▼──────┐  ┌───▼──────┐      │
    │ usersdb  │  │ walletdb │  │ offerdb  │  │bookingdb │      │
    │  MySQL   │  │  MySQL   │  │  MySQL   │  │  MySQL   │      │
    └──────────┘  └──────────┘  └──────────┘  └──────────┘      │
                                                                  │
         ┌────────────────────────────────────────────────────────┘
         │
    ┌────▼──────────┐
    │ Notification  │
    │   Service     │
    │    (8086)     │
    │    Spring     │
    └────┬──────────┘
         │
    ┌────▼──────────┐
    │notificationdb │
    │    MySQL      │
    └───────────────┘

         ┌──────────────────────────────────────────┐
         │         RabbitMQ (5672)                  │
         │                                          │
         │  bookings.exchange (fanout)              │
         │    ├─ notification.queue                 │
         │    ├─ provider.match.queue               │
         │    └─ wallet.worker.queue                │
         │                                          │
         │  payments.exchange (direct)              │
         │    ├─ customer.notification.queue        │
         │    └─ admin.notification.queue           │
         └──────────────────────────────────────────┘
```

---

## 10. Troubleshooting

### Admin Service returns empty arrays
- Ensure User Service (8081) and Booking Service (8084) are running
- Check `services.*.base-url` in `admin-service/src/main/resources/application.properties`

### Notification Service not receiving events
- Verify RabbitMQ is running: http://localhost:15672
- Check queues exist: `notification.queue`, `customer.notification.queue`, `provider.match.queue`
- Ensure Booking Service (8084) is running and publishing events
- Check Notification Service logs for RabbitMQ connection errors

### API Gateway returns 503 Service Unavailable
- Ensure the target service is running on the expected port
- Check gateway logs for connection errors
- Verify `application.yml` routes match the service ports

### MySQL connection errors
- Ensure MySQL is running on port 3306
- Verify root password matches `application.properties` (`Raghad@20226041`)
- Database `notificationdb` is created automatically — no manual setup needed

### Payment failure alerts not appearing
- Trigger a payment failure by attempting a booking with insufficient balance
- Check Admin Service logs for RabbitMQ message receipt
- Verify `admin.notification.queue` is bound to `payments.exchange` with key `payment.failed`

---

## 11. Postman Collection Variables

The collection uses these variables (pre-configured):

| Variable | Value | Description |
|----------|-------|-------------|
| `GATEWAY_URL` | `http://localhost:8080` | API Gateway base URL |
| `ADMIN_URL` | `http://localhost:8085` | Admin Service direct URL |
| `NOTIFICATION_URL` | `http://localhost:8086` | Notification Service direct URL |
| `USER_ID` | `1` | Sample user ID for testing |

Update `USER_ID` after registering users to match your test data.

---

## 12. Summary

Member 3 completes the distributed system with:

✅ **Admin Service** — aggregates data from all services, receives payment failure alerts  
✅ **Notification Service** — consumes RabbitMQ events, persists notifications, exposes JSON API  
✅ **API Gateway** — single entry point, path rewriting, CORS support  
✅ **RabbitMQ Integration** — consumes from 3 queues, handles 3 event types  
✅ **Team-of-3 Features** — service request matching notifications, admin payment alerts  
✅ **Postman Collection** — comprehensive test suite for all endpoints  

All functional requirements for Member 3 are implemented and tested.
