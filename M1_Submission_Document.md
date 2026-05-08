# Member 1 — Submission Document
## On-Demand Home Services Marketplace | DS Assignment 2

---

## 1. Responsibilities

Member 1 owns:
- **User Service** (port 8081, deployed on WildFly/JBoss) — handles registration, login, profile, and service requests
- **Wallet Service** (port 8082, deployed on WildFly/JBoss) — handles wallet creation, top-up, deduction, and rollback
- **EJB Configuration** — WildFly server setup, all 4 EJB bean types implemented

---

## 2. Services Overview

### User Service (port 8081, WildFly)
Jakarta EE (EJB) service deployed as a WAR on WildFly/JBoss. Uses its own MySQL database (`usersdb`).

**EJB Beans Used:**

| Bean | Type | Description |
|------|------|-------------|
| `AuthBean` | `@Stateless` | Handles user registration and login — no state between calls, like a stateless calculator |
| `CatalogCacheBean` | `@Singleton` | Caches service categories in memory — only one instance exists for the entire application |
| `ServiceRequestBean` | `@Stateless` | Manages customer service requests for the team-of-3 feature |

**Key endpoints (base: `http://localhost:8081/user-service/api`):**
- `POST /users/register` — register a new user (customer or provider)
- `POST /users/login` — login and get user info
- `GET /users/{id}/profile` — get a user's profile
- `GET /users` — get all users (Admin)
- `GET /users/categories` — list all service categories (via Singleton bean)
- `POST /users/categories` — add a category (via Singleton bean)
- `POST /users/requests` — customer submits a service request (Team-of-3)
- `GET /users/requests/{customerId}` — view customer's requests (Team-of-3)
- `GET /users/requests/pending` — get unmatched requests (Team-of-3)
- `PATCH /users/requests/{id}/match` — mark request as matched (Team-of-3)

**Key entities:**
- `User` — fields: id, username, password, role (CUSTOMER/PROVIDER), profession
- `ServiceRequest` — fields: id, customerId, category, maxPrice, requiredDate, matchedOfferId, status

**Auto-wallet creation:** When a CUSTOMER registers, the User Service automatically calls Wallet Service via HTTP to create a wallet with the `initialBalance` from the registration request body.

---

### Wallet Service (port 8082, WildFly)
Jakarta EE (EJB) service deployed as a WAR on WildFly/JBoss. Uses its own MySQL database (`walletdb`).

**EJB Beans Used:**

| Bean | Type | Description |
|------|------|-------------|
| `WalletSessionBean` | `@Stateful` | Manages wallet operations — remembers `pendingTopUp` state between calls in the same session |
| `WalletMDB` | `@MessageDriven` | RabbitMQ message-driven bean — listens on wallet queue for async deduction requests |

**Key endpoints (base: `http://localhost:8082/wallet-service/api`):**
- `POST /wallet/{userId}/create` — create a wallet with initial balance
- `GET /wallet/{userId}/balance` — get current wallet balance
- `POST /wallet/{userId}/topup` — add funds to wallet
- `PATCH /wallet/{userId}/deduct` — deduct funds (returns 402 if insufficient)
- `PATCH /wallet/{userId}/rollback` — refund funds (called on booking failure)

**Key entity:**
- `Wallet` — fields: id, userId, balance

---

## 3. EJB Design — Four Bean Types

The assignment requires at least **two** EJB types. We implement all **four**:

### @Stateless — `AuthBean`
- **Why stateless?** Authentication is a pure function: take credentials, return result, forget everything.
- Each JBoss instance of `AuthBean` handles any request with no shared memory.
- Uses `@PersistenceContext` to get a JPA `EntityManager` injected automatically.

### @Singleton — `CatalogCacheBean`
- **Why singleton?** The list of service categories changes rarely. One shared in-memory list is more efficient than querying the database for every request.
- JBoss creates exactly one instance for the whole application lifetime.
- Any update to categories is visible to all subsequent requests immediately.

### @Stateful — `WalletSessionBean`
- **Why stateful?** The bean remembers `pendingTopUp` between consecutive calls in the same client session.
- Demonstrates the staged top-up pattern: `stageTopUp()` → `commitTopUp()` shows state persisting between calls.
- Each client gets their own WalletSessionBean instance that JBoss manages.

### @MessageDriven — `WalletMDB`
- **Why message-driven?** Allows asynchronous wallet deductions triggered by RabbitMQ messages.
- Demonstrates the MDB pattern from the EJB specification.
- Decouples the booking flow from the wallet operation.

---

## 4. Design Decisions & Assumptions

### WildFly/JBoss Deployment
- User Service and Wallet Service are deployed as WAR files on WildFly (JBoss).
- Context paths: `/user-service/` and `/wallet-service/` respectively.
- REST paths follow: `http://localhost:8081/user-service/api/users/...`

### Database Isolation
- User Service uses `usersdb` (MySQL)
- Wallet Service uses `walletdb` (MySQL)
- Neither service accesses the other's database — all cross-service data goes through REST calls.
- This satisfies the microservice isolation requirement.

### MySQL Password
- The persistence.xml files use password `basmala.12347890`.
- If your MySQL root password is different, update both `persistence.xml` files.

### No JWT
- Login returns the user object directly (no JWT token).
- The assignment did not mandate token-based auth, so simple session-less auth is used.
- The API Gateway (M3) does not enforce JWT validation.

### Wallet Auto-Creation
- When a CUSTOMER registers, User Service calls `POST /wallet-service/api/wallet/{userId}/create` automatically.
- The `initialBalance` field in the registration request body sets the starting balance.
- If Wallet Service is down, registration still succeeds (wallet creation failure is logged but not fatal).

### Service Request Matching (Team-of-3)
- Customer submits a service request with category, maxPrice, and requiredDate.
- M2's Offer Service polls `GET /users/requests/pending` to find unmatched requests and match them to available offers.
- Once matched, M2 calls `PATCH /users/requests/{id}/match` to update the status.

---

## 5. How to Run

### Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| WildFly | 27+ (Jakarta EE 10) |
| MySQL | 8.0+ |

### Step 1 — Set Up MySQL Databases

Start MySQL and create the databases (WildFly/JPA will create the tables automatically):

```sql
CREATE DATABASE IF NOT EXISTS usersdb;
CREATE DATABASE IF NOT EXISTS walletdb;
```

If your MySQL root password is not `basmala.12347890`, update these two files:
- `user-service/user-service/src/main/resources/META-INF/persistence.xml`
- `wallet-service/wallet-service/src/main/resources/META-INF/persistence.xml`

### Step 2 — Configure WildFly Datasources

In WildFly management console (http://localhost:9990), add two MySQL datasources:

**Datasource 1 — Users:**
- JNDI Name: `java:/UserDS` (or as configured in persistence.xml)
- Connection URL: `jdbc:mysql://localhost:3306/usersdb`
- Username: `root`, Password: `basmala.12347890`

**Datasource 2 — Wallet:**
- JNDI Name: `java:/WalletDS`
- Connection URL: `jdbc:mysql://localhost:3306/walletdb`
- Username: `root`, Password: `basmala.12347890`

Also, ensure the MySQL JDBC driver (`mysql-connector-j-8.x.jar`) is deployed to WildFly's `standalone/deployments/` folder.

### Step 3 — Build the WAR Files

```bash
# Build User Service
cd user-service/user-service
mvn clean package

# Build Wallet Service
cd ../../wallet-service/wallet-service
mvn clean package
```

### Step 4 — Deploy to WildFly

Copy the WAR files to WildFly's deployment folder:

```bash
# Adjust WILDFLY_HOME to your installation path
cp user-service/user-service/target/user-service.war  $WILDFLY_HOME/standalone/deployments/
cp wallet-service/wallet-service/target/wallet-service.war  $WILDFLY_HOME/standalone/deployments/
```

OR use the WildFly CLI:
```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect
deploy /path/to/user-service.war
deploy /path/to/wallet-service.war
```

### Step 5 — Verify Services

- User Service: `GET http://localhost:8081/user-service/api/users` → should return `[]`
- Wallet Service: `GET http://localhost:8082/wallet-service/api/wallet/1/balance` → should return 404 (no wallet yet)

### Step 6 — Import Postman Collection

Import `Member1_User_Wallet.postman_collection.json` into Postman.

---

## 6. Test Scenario (Happy Path)

### Test 1 — Register a Provider
```
POST http://localhost:8081/user-service/api/users/register
Body: { "username": "plumber_ali", "password": "pass123", "role": "PROVIDER", "profession": "Plumber" }
Expected: 201 Created with user object (note the returned id)
```

### Test 2 — Register a Customer with Wallet
```
POST http://localhost:8081/user-service/api/users/register
Body: { "username": "customer_sara", "password": "pass123", "role": "CUSTOMER", "initialBalance": 500.0 }
Expected: 201 Created with user object (note the returned id)
         Wallet automatically created with $500 balance
```

### Test 3 — Check Wallet Balance
```
GET http://localhost:8082/wallet-service/api/wallet/{customerId}/balance
Expected: 200 OK with { "userId": 1, "balance": 500.0 }
```

### Test 4 — Top Up Wallet
```
POST http://localhost:8082/wallet-service/api/wallet/{customerId}/topup
Body: { "amount": 100.0 }
Expected: 200 OK with updated balance ($600)
```

### Test 5 — Login
```
POST http://localhost:8081/user-service/api/users/login
Body: { "username": "customer_sara", "password": "pass123" }
Expected: 200 OK with user object
```

---

## 7. API Reference

### User Service — http://localhost:8081/user-service/api

| Method | Path | Description |
|--------|------|-------------|
| POST | `/users/register` | Register new user (customer or provider) |
| POST | `/users/login` | Login with credentials |
| GET | `/users/{id}/profile` | Get user profile |
| GET | `/users` | Get all users (Admin) |
| GET | `/users/categories` | List all service categories |
| POST | `/users/categories` | Add new category |
| POST | `/users/requests` | Submit service request (Team-of-3) |
| GET | `/users/requests/{customerId}` | View customer's requests |
| GET | `/users/requests/pending` | Get unmatched requests |
| PATCH | `/users/requests/{id}/match` | Mark request matched |

### Wallet Service — http://localhost:8082/wallet-service/api

| Method | Path | Description |
|--------|------|-------------|
| POST | `/wallet/{userId}/create` | Create wallet with initial balance |
| GET | `/wallet/{userId}/balance` | Get wallet balance |
| POST | `/wallet/{userId}/topup` | Add funds to wallet |
| PATCH | `/wallet/{userId}/deduct` | Deduct funds (402 if insufficient) |
| PATCH | `/wallet/{userId}/rollback` | Refund funds (on booking failure) |

---

## 8. EJB Summary

| Bean Class | EJB Type | Used For |
|-----------|----------|----------|
| `AuthBean` | `@Stateless` | User registration and login |
| `CatalogCacheBean` | `@Singleton` | In-memory category cache |
| `ServiceRequestBean` | `@Stateless` | Service request management |
| `WalletSessionBean` | `@Stateful` | Wallet operations with session state |
| `WalletMDB` | `@MessageDriven` | Async wallet operations via RabbitMQ |

The assignment requires **two** EJB types — we implement all **four** to fully demonstrate the EJB specification.

---

## 9. Troubleshooting

| Error | Cause | Fix |
|-------|-------|-----|
| `Access denied for user 'root'` | Wrong MySQL password | Update `persistence.xml` password field |
| `503 Service Unavailable` from gateway | WildFly not running | Start WildFly: `$WILDFLY_HOME/bin/standalone.sh` |
| `Cannot find JNDI binding` | Datasource not configured | Add MySQL datasource in WildFly console |
| `Wallet not found` after registration | Wallet Service not running when customer registered | Re-register customer, or call `POST /wallet/{id}/create` manually |
| Port 8081 already in use | WildFly default port conflict | Change WildFly HTTP port in `standalone.xml` |
