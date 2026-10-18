# User & Wallet Services (EJB) — On-Demand Home Services Marketplace

Part of a distributed on-demand home services marketplace built for a Distributed Systems course (microservices, EJB, RabbitMQ), Cairo University. **Team project (3 members).** This branch contains **my part (Member 1): the User Service and the Wallet Service.**

## 🧩 My Contribution (Member 1)
I built two EJB microservices on **Jakarta EE 10 / JBoss EAP 8.0**, each with its own MySQL database:

| Service | Port | Database | EJB beans |
|---|---|---|---|
| User Service | 8081 | `usersdb` | `@Stateless` AuthBean, `@Singleton` CatalogCacheBean, `@Stateless` ServiceRequestBean |
| Wallet Service | 8082 | `walletdb` | `@Stateful` WalletSessionBean, `@Singleton` + `@Startup` WalletMDB |

- **User Service:** registration (customer or provider), login, profiles, listing all users (consumed by the Admin Service), and service categories kept in an in-memory singleton cache that is preloaded at startup and can be extended at runtime. A wallet is created automatically when a customer registers.
- **Wallet Service:** create wallet, top-up, balance, deduct and rollback. The stateful bean keeps a staged top-up across calls in the same client session.
- **RabbitMQ consumer:** WalletMDB listens on `wallet.worker.queue` (bound to the `bookings.exchange` fanout). It deducts the amount on a `BOOKING_CONFIRMED` event and refunds on `PAYMENT_FAILED`, which keeps the wallet consistent with the booking state.
- **Team-of-3 feature:** customer service requests (category, max price, required date) are stored with a `PENDING` status. The Offer Service reads pending requests and marks them `MATCHED` through my endpoints.
- Wrote the Postman collection for all 15 endpoints (10 user, 5 wallet).

## 🔌 REST API
**User Service** (`http://localhost:8081/user-service/api`)

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

**Wallet Service** (`http://localhost:8082/wallet-service/api`)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/wallet/{userId}/create` | Create a wallet (409 if it exists) |
| GET | `/wallet/{userId}/balance` | Current balance |
| POST | `/wallet/{userId}/topup` | Add funds |
| PATCH | `/wallet/{userId}/deduct` | Deduct (402 if insufficient) |
| PATCH | `/wallet/{userId}/rollback` | Refund |

## 🛠️ Tech Stack
Jakarta EE 10 (EJB, JAX-RS, JPA/Hibernate), JBoss EAP 8.0, MySQL 8, RabbitMQ, Maven, Postman

## 🚀 How to Run
**Prerequisites:** JDK 17, Maven, JBoss EAP 8.0, MySQL 8 on port 3306, RabbitMQ on port 5672.

1. Create the databases: `CREATE DATABASE usersdb; CREATE DATABASE walletdb;` (Hibernate creates the tables).
2. Set your MySQL credentials in `persistence.xml` of both services.
3. Build each service with `mvn clean package`.
4. Create a second server directory by copying `standalone` to `standalone-user`.
5. Deploy `user-service.war` to `standalone-user/deployments` and `wallet-service.war` to `standalone/deployments`.
6. Start two instances:
```bash
   standalone.bat -Djboss.socket.binding.port-offset=1 -Djboss.server.base.dir=<JBOSS_HOME>/standalone-user   # User Service (8081)
   standalone.bat -Djboss.socket.binding.port-offset=2                                                         # Wallet Service (8082)
```
7. Import `Member1_UserWallet.json` into Postman.

## ⚠️ Known Limitations
This is an academic project. Passwords are stored as plain text, and the services do not implement token-based authentication themselves.

## 👥 Team
- **Amira** — User Service and Wallet Service (this branch)
- **Basmala** — Offer Service and Booking Service (Spring Boot)
- **Raghad** — Admin Service, Notification Service and API Gateway

Full system and other members' work: [original team repository](https://github.com/basmala352004/On-Demand-Home-Services-Marketplace-Platform)
