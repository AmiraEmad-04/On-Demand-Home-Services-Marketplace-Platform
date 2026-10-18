# On-Demand Home Services Marketplace Platform

A distributed marketplace where customers book skilled professionals (plumbers, carpenters, electricians, cleaners, painters) for home services. Built as a **Distributed Systems course assignment (Winter 2026, Cairo University)** to practice **microservices, EJBs and asynchronous messaging with RabbitMQ**. **Team project (3 members).**

> **My part (Member 1): the User Service and the Wallet Service**, built as EJB microservices. Details below.

## 🏗️ System Overview

| Service | Port | Stack | Owner |
|---|---|---|---|
| **User Service** | 8081 | Jakarta EE 10 / EJB, MySQL | **Amira** |
| **Wallet Service** | 8082 | Jakarta EE 10 / EJB, MySQL, RabbitMQ consumer | **Amira** |
| Offer Service | 8083 | Spring Boot | Basmala |
| Booking Service | 8084 | Spring Boot, RabbitMQ publisher | Basmala |
| Admin Service | 8085 | Spring Boot (no database), RabbitMQ consumer | Raghad |
| Notification Service | 8086 | Spring Boot, MySQL, RabbitMQ consumer | Raghad |
| API Gateway | 8080 | Spring Cloud Gateway | Raghad |

Each service has its own codebase and its own database, and services talk to each other only through REST calls or RabbitMQ events.

```
                    API Gateway (8080)
   ┌────────┬────────┬────────┬────────┬────────┬──────────────┐
 User     Wallet   Offer   Booking   Admin   Notification
 (8081)   (8082)   (8083)  (8084)    (8085)  (8086)
              └──────────── RabbitMQ ─────────────┘
```

**RabbitMQ topology**
- `bookings.exchange` (fanout) → `notification.queue`, `provider.match.queue`, `wallet.worker.queue`
- `payments.exchange` (direct, key `payment.failed`) → `customer.notification.queue`, `admin.notification.queue`
- Events: `BOOKING_CONFIRMED`, `PAYMENT_FAILED`, `SERVICE_REQUEST_MATCHED`

## 🧩 My Contribution (Member 1)

I designed and built the **User Service** and the **Wallet Service** using **EJBs on JBoss EAP 8.0**, covering all four EJB bean types required by the assignment across the two services.

| Service | EJB beans | What it does |
|---|---|---|
| User Service | `@Stateless` **AuthBean** | Register (customer or provider), login, profile, list all users |
| User Service | `@Singleton` **CatalogCacheBean** | Service categories (Plumbing, Carpentry, Electrical, Cleaning, Painting) in a shared in-memory cache, preloaded at startup, extendable at runtime |
| User Service | `@Stateless` **ServiceRequestBean** | Customer service requests (category, max price, required date) |
| Wallet Service | `@Stateful` **WalletSessionBean** | Create wallet, top-up, balance, deduct, rollback; keeps a staged top-up across calls in one client session |
| Wallet Service | `@Singleton` + `@Startup` **WalletMDB** | RabbitMQ listener on `wallet.worker.queue`: deducts on `BOOKING_CONFIRMED`, refunds on `PAYMENT_FAILED` |

**Design decisions**
- AuthBean is stateless because every authentication call is self-contained, so the container can serve requests from a pool of instances.
- The listener is a `@Singleton` + `@Startup` bean instead of a standard `@MessageDriven` bean, because JBoss EAP 8.0 needs an extra resource adapter configuration for native RabbitMQ.
- A wallet is created automatically when a customer registers. Providers do not get one.
- **Team-of-3 feature (customer side):** requests are saved as `PENDING`. The Offer Service reads them through my endpoints and marks them `MATCHED` once a matching offer is found.
- I also wrote the Postman collection covering all 15 endpoints of my two services.

**REST API: User Service** (`http://localhost:8081/user-service/api`)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/users/register` | Register a customer or provider |
| POST | `/users/login` | Login |
| GET | `/users/{id}/profile` | Get a profile |
| GET | `/users` | List all users |
| GET / POST | `/users/categories` | List / add service categories |
| POST | `/users/requests` | Submit a service request |
| GET | `/users/requests/{customerId}` | A customer's requests |
| GET | `/users/requests/pending` | Unmatched requests |
| PATCH | `/users/requests/{id}/match` | Mark a request as matched |

**REST API: Wallet Service** (`http://localhost:8082/wallet-service/api`)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/wallet/{userId}/create` | Create a wallet (409 if it exists) |
| GET | `/wallet/{userId}/balance` | Current balance |
| POST | `/wallet/{userId}/topup` | Add funds |
| PATCH | `/wallet/{userId}/deduct` | Deduct (402 if insufficient) |
| PATCH | `/wallet/{userId}/rollback` | Refund |

## 🔄 How the Services Work Together
1. A customer registers through the User Service, and a wallet is created automatically.
2. A provider creates a service offer (Offer Service). Customers browse offers by category.
3. The customer books an offer. The Booking Service validates the offer and the wallet balance, processes the payment, marks the offer as booked and publishes events to RabbitMQ.
4. On success, both customer and provider get a notification. On insufficient balance or an unavailable offer, the booking is rejected, the amount is rolled back, the customer is notified and the admin receives a payment-failure alert.
5. Team-of-3 flow: a customer posts a service request, the Offer Service matches it to an offer and the provider is notified through RabbitMQ.

## 👥 Team

| Member | Services | Highlights |
|---|---|---|
| **Amira (Member 1)** | User Service, Wallet Service | EJB design (Stateless, Singleton, Stateful, RabbitMQ listener), wallet consistency, service requests |
| **Basmala (Member 2)** | Offer Service, Booking Service | Offer listings and categories, booking orchestration with rollback, RabbitMQ publishers, request matching |
| **Raghad (Member 3)** | Admin Service, Notification Service, API Gateway | Admin aggregation and payment-failure alerts, persisted notifications, single entry point with path rewriting |

## 🛠️ Tech Stack
Java 17, Jakarta EE 10 (EJB, JAX-RS, JPA), JBoss EAP 8.0, Spring Boot, Spring Cloud Gateway, MySQL, H2, RabbitMQ, Maven, Docker, Postman

## 🚀 How to Run
**Prerequisites:** JDK 17, Maven 3.9+, JBoss EAP 8.0, MySQL 8, Docker (for RabbitMQ).

1. **RabbitMQ:** `docker run -it --rm --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4.0-management`
2. **Databases:** `CREATE DATABASE usersdb; CREATE DATABASE walletdb;` and set your own MySQL credentials in each service's configuration.
3. **My services (Member 1):** build both with `mvn clean package`, deploy `user-service.war` to a second server directory (copy of `standalone`) and `wallet-service.war` to the default one, then start two instances with port offsets 1 and 2.
4. **Offer and Booking services (Member 2):** `mvn spring-boot:run` in each folder.
5. **Admin, Notification and Gateway (Member 3):** `mvn spring-boot:run` in each folder.
6. Import the Postman collections (one per member) and test through `http://localhost:8080`.

## ⚠️ Known Limitations
This is an academic project: passwords are stored as plain text, and token-based authentication (JWT) is not implemented. Credentials in configuration files must be replaced with your own before running.

## 📁 Repository Layout
- `user-service/`, `wallet-service/`: my services
- `<<ضيفي مكان باقي الخدمات أو أسماء البرانشات اللي فيها شغل Basmala وRaghad>>`

Original team repository: <https://github.com/basmala352004/On-Demand-Home-Services-Marketplace-Platform>
