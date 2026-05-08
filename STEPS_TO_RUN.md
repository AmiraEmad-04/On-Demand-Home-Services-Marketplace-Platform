# Steps to Run — Complete System Guide
## On-Demand Home Services Marketplace | DS Assignment 2

---

## System Architecture

| Service | Member | Technology | Port | Context Path |
|---------|--------|-----------|------|-------------|
| API Gateway | M3 | Spring Cloud Gateway | 8080 | / |
| User Service | M1 | Jakarta EE EJB (WildFly) | 8081 | /user-service/api |
| Wallet Service | M1 | Jakarta EE EJB (WildFly) | 8082 | /wallet-service/api |
| Offer Service | M2 | Spring Boot | 8083 | /api |
| Booking Service | M2 | Spring Boot | 8084 | /api |
| Admin Service | M3 | Spring Boot | 8085 | /api |
| Notification Service | M3 | Spring Boot | 8086 | /api |

---

## Prerequisites

Install all of these before starting:

| Tool | Version | Used For |
|------|---------|----------|
| Java | 17+ | All services |
| Maven | 3.9+ | Building all services |
| WildFly | 27+ (Jakarta EE 10) | M1 — User + Wallet services |
| MySQL | 8.0+ | All services with databases |
| Docker | Any recent | Running RabbitMQ |
| Postman | Any recent | Testing all endpoints |

---

## Step 1 — Start MySQL

Ensure MySQL is running on port 3306.

Create the required databases (JPA will create tables automatically):

```sql
CREATE DATABASE IF NOT EXISTS usersdb;
CREATE DATABASE IF NOT EXISTS walletdb;
CREATE DATABASE IF NOT EXISTS offerdb;
CREATE DATABASE IF NOT EXISTS bookingdb;
CREATE DATABASE IF NOT EXISTS notificationdb;
```

**Password note:** Each service uses a different password in its config:
- M1 services (User + Wallet): password is `basmala.12347890` (in `persistence.xml`)
- M2/M3 services (Offer, Booking, Notification): password is `Raghad@20226041` (in `application.properties`)

If your MySQL root password is different, update these files before running:
- `user-service/user-service/src/main/resources/META-INF/persistence.xml`
- `wallet-service/wallet-service/src/main/resources/META-INF/persistence.xml`
- `offer-service/src/main/resources/application.properties`
- `booking-service/src/main/resources/application.properties`
- `notification-service/src/main/resources/application.properties`

---

## Step 2 — Start RabbitMQ

Run RabbitMQ using Docker:

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:4.0-management
```

Verify it is running: open http://localhost:15672
- Username: `guest`
- Password: `guest`

You should see the RabbitMQ management dashboard.

---

## Step 3 — Deploy M1 Services (WildFly/JBoss)

### 3a — Add MySQL Driver to WildFly

Download `mysql-connector-j-8.x.jar` and place it in WildFly's deployment or add as a module.

### 3b — Configure Datasources in WildFly

Open the WildFly management console at http://localhost:9990 and add two datasources:

**Users Datasource:**
- Name: `UsersDS`
- JNDI: `java:/UserDS`
- Driver: MySQL
- URL: `jdbc:mysql://localhost:3306/usersdb`
- User/Pass: `root` / `basmala.12347890`

**Wallet Datasource:**
- Name: `WalletDS`
- JNDI: `java:/WalletDS`
- Driver: MySQL
- URL: `jdbc:mysql://localhost:3306/walletdb`
- User/Pass: `root` / `basmala.12347890`

### 3c — Build and Deploy WARs

```bash
# Build User Service
cd user-service/user-service
mvn clean package -DskipTests
# Copy WAR to WildFly deployments
cp target/user-service.war $WILDFLY_HOME/standalone/deployments/

# Build Wallet Service
cd ../../wallet-service/wallet-service
mvn clean package -DskipTests
cp target/wallet-service.war $WILDFLY_HOME/standalone/deployments/
```

### 3d — Verify M1 Services

```
GET http://localhost:8081/user-service/api/users         → should return []
GET http://localhost:8082/wallet-service/api/wallet/1/balance  → should return 404 (no wallet yet)
```

---

## Step 4 — Start M2 Services (Spring Boot)

Open two terminals:

**Terminal 1 — Offer Service (port 8083):**
```bash
cd offer-service
mvn spring-boot:run
```
Verify: `GET http://localhost:8083/api/offers/categories`
Should return the 5 seeded categories: Plumbing, Carpentry, Electrical, Cleaning, Painting

**Terminal 2 — Booking Service (port 8084):**
```bash
cd booking-service
mvn spring-boot:run
```
Verify: `GET http://localhost:8084/api/bookings/all`
Should return `[]`

---

## Step 5 — Start M3 Services (Spring Boot)

Open three more terminals:

**Terminal 3 — Notification Service (port 8086):**
Start this BEFORE Admin Service so queues are declared.
```bash
cd notification-service
mvn spring-boot:run
```
Verify: `GET http://localhost:8086/api/notifications/health`
Should return `{ "service": "notification-service", "status": "UP" }`

**Terminal 4 — Admin Service (port 8085):**
```bash
cd admin-service
mvn spring-boot:run
```
Verify: `GET http://localhost:8085/api/admin/users`
Should return the list of users from User Service.

**Terminal 5 — API Gateway (port 8080):**
```bash
cd api-gateway
mvn spring-boot:run
```
Verify: `GET http://localhost:8080/actuator/health`
Should return `{ "status": "UP" }`

---

## Step 6 — Import Postman Collections

Import all files into Postman (File → Import → select all at once):
- `postman_collection/Member1_User_Wallet.postman_collection.json`
- `postman_collection/Member2_Offer_Booking.postman_collection.json`
- `postman_collection/Member3_Admin_Notification_Gateway.postman_collection.json`
- `postman_collection/DS_Assignment_Environment.postman_environment.json`

In Postman, select the **"DS Assignment — Local"** environment from the top-right dropdown.

---

## Step 7 — Run the Full Test Sequence

Run these in order. This covers all M3 test cases as specified in the assignment.

### TEST 1 — Admin Views All Users

```
GET http://localhost:8085/api/admin/users
```
Expected: empty array `[]` (no users yet)

---

### TEST 2 — Admin Adds a Category

```
POST http://localhost:8085/api/admin/categories
Body: { "name": "Gardening" }
```
Expected: `201 Created` with the new category object.

Verify in Offer Service:
```
GET http://localhost:8083/api/offers/categories
```
Should now show Plumbing, Carpentry, Electrical, Cleaning, Painting, **Gardening**.

---

### TEST 3 — Full Booking Flow + Notifications

#### 3a — Register a Provider (via Gateway)

```
POST http://localhost:8080/users/register
Body:
{
  "username": "plumber_ali",
  "password": "pass123",
  "role": "PROVIDER",
  "profession": "Plumber"
}
```
Note the `id` returned — e.g., **providerId = 1** (or 2, depending on order)

#### 3b — Register a Customer (via Gateway)

```
POST http://localhost:8080/users/register
Body:
{
  "username": "customer_sara",
  "password": "pass123",
  "role": "CUSTOMER",
  "initialBalance": 500.0
}
```
Note the `id` returned — e.g., **customerId = 1** (or 2)
Wallet is automatically created with $500.

#### 3c — Provider Creates a Listing (via Gateway)

```
POST http://localhost:8080/offers
Body:
{
  "providerId": <providerId>,
  "categoryId": 1,
  "price": 120.0,
  "availableDate": "2026-06-20T10:00:00",
  "notes": "Expert plumbing"
}
```
Note the `offerId` returned — e.g., **offerId = 1**

#### 3d — Customer Places a Booking (via Gateway)

```
POST http://localhost:8080/bookings
Body:
{
  "customerId": <customerId>,
  "offerId": <offerId>
}
```
Expected: `201 Created` with booking record.
- Wallet deducted: $500 - $120 = $380
- Listing status changes to BOOKED
- RabbitMQ publishes BOOKING_CONFIRMED event

#### 3e — Check Customer Notifications ← Proves Notification Service works

```
GET http://localhost:8086/api/notifications/<customerId>
```
Expected:
```json
[
  {
    "id": 1,
    "userId": <customerId>,
    "type": "BOOKING_CONFIRMED",
    "message": "Your booking #1 has been confirmed! Amount charged: 120.0.",
    "createdAt": "2026-05-..."
  }
]
```

#### 3f — Check Provider Notifications

```
GET http://localhost:8086/api/notifications/<providerId>
```
Expected:
```json
[
  {
    "id": 2,
    "userId": <providerId>,
    "type": "BOOKING_CONFIRMED",
    "message": "You have a new confirmed booking #1! Customer ID: <customerId>, Amount: 120.0.",
    "createdAt": "2026-05-..."
  }
]
```

#### 3g — Admin Views All Bookings

```
GET http://localhost:8085/api/admin/bookings
```
Expected: array with the booking you just created.

---

### TEST 4 — Payment Failure → Admin Alert

#### 4a — Create a New High-Priced Offer

```
POST http://localhost:8080/offers
Body:
{
  "providerId": <providerId>,
  "categoryId": 1,
  "price": 9999.0,
  "availableDate": "2026-07-01T10:00:00"
}
```
Note the new `offerId` — e.g., **offerId = 2**

#### 4b — Try to Book It (Customer Only Has $380 Left)

```
POST http://localhost:8080/bookings
Body:
{
  "customerId": <customerId>,
  "offerId": 2
}
```
Expected: `402 Payment Required` with error message about insufficient balance.

#### 4c — Check Admin Alerts ← Proves Direct Exchange Routing Works

```
GET http://localhost:8085/api/admin/alerts
```
Expected:
```json
[
  {
    "eventType": "PAYMENT_FAILED",
    "customerId": <customerId>,
    "offerId": 2,
    "attemptedAmount": 9999.0,
    "reason": "INSUFFICIENT_BALANCE",
    "attemptedAt": "2026-05-..."
  }
]
```

#### 4d — Check Customer Notification for the Failure

```
GET http://localhost:8086/api/notifications/<customerId>
```
Expected: **2 notifications** — the BOOKING_CONFIRMED from before + a new PAYMENT_FAILED one.

---

## Startup Order Summary

```
1. MySQL
2. RabbitMQ (Docker)
3. WildFly → deploy user-service.war + wallet-service.war
4. Offer Service (mvn spring-boot:run in offer-service/)
5. Booking Service (mvn spring-boot:run in booking-service/)
6. Notification Service (mvn spring-boot:run in notification-service/)
7. Admin Service (mvn spring-boot:run in admin-service/)
8. API Gateway (mvn spring-boot:run in api-gateway/)
```

---

## Service Port Reference

| URL | Service | What to verify |
|-----|---------|---------------|
| http://localhost:8080/actuator/health | API Gateway | `{"status":"UP"}` |
| http://localhost:8081/user-service/api/users | User Service | `[]` |
| http://localhost:8082/wallet-service/api/wallet/1/balance | Wallet Service | `404 Wallet not found` |
| http://localhost:8083/api/offers/categories | Offer Service | 5 categories |
| http://localhost:8084/api/bookings/all | Booking Service | `[]` |
| http://localhost:8085/api/admin/users | Admin Service | `[]` |
| http://localhost:8086/api/notifications/health | Notification Service | `{"status":"UP"}` |
| http://localhost:15672 | RabbitMQ Dashboard | guest/guest |

---

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|---------|
| `Access denied for user 'root'` | Wrong MySQL password | Update `persistence.xml` and `application.properties` |
| `Payment deduction failed: Connection error` | WildFly/User/Wallet service not running | Start WildFly and verify deployment |
| `Offer unavailable (status=BOOKED)` | Offer already used | Create a new offer with `POST /api/offers` |
| `Admin users returns []` | User Service not running or wrong URL | Check port 8081 and `services.user-service.base-url` in admin `application.properties` |
| `Notifications not appearing` | RabbitMQ not running or Notification Service not started | Verify RabbitMQ at :15672, restart notification-service |
| `503 from Gateway` | Target service is down | Start the service on the expected port |
| `409 Conflict on category` | Category already exists | That is correct — categories are unique |
| `402 Payment Required` | Insufficient wallet balance | Top up wallet with `POST /wallet/{id}/topup` |
