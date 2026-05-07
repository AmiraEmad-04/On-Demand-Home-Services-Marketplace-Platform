# Steps to Run M2 Services — Complete Guide

## Prerequisites (install if not already done)
- Java 17+ (you have 25)
- MySQL 8 running on port 3306
- RabbitMQ running on port 5672
- M1's WildFly/JBoss server running on port 8082

---

## Step 1 — Fix MySQL Password

Open BOTH files and change `spring.datasource.password=root` to your actual MySQL password:

- `offer-service/src/main/resources/application.properties`
- `booking-service/src/main/resources/application.properties`

To find/reset your MySQL password, open MySQL Workbench or run:
```
mysql -u root -p
```
If `root` doesn't work, reset it via MySQL Command Line Client:
```sql
ALTER USER 'root'@'localhost' IDENTIFIED BY 'root';
FLUSH PRIVILEGES;
```

---

## Step 2 — Start Services in Order

Start them in this exact order:

1. **RabbitMQ** — must be first
   - Windows: Start from Start Menu or `rabbitmq-server.bat`
   - Verify: open http://localhost:15672 (user: guest / pass: guest)

2. **M1 — WildFly/JBoss** (your partner's services — user-service and wallet-service)
   - The wallet-service must be accessible at http://localhost:8082
   - Verify with: GET http://localhost:8082/wallet-service/api/wallet/1/balance

3. **M2 — Offer Service** (port 8083)
   - In IntelliJ: open `offer-service`, run `OfferServiceApplication`
   - OR in terminal: `cd offer-service && mvn spring-boot:run`
   - Verify: GET http://localhost:8083/api/offers/categories (should return Plumbing, Carpentry, etc.)

4. **M2 — Booking Service** (port 8084)
   - In IntelliJ: open `booking-service`, run `BookingServiceApplication`
   - OR in terminal: `cd booking-service && mvn spring-boot:run`
   - Verify: GET http://localhost:8084/api/bookings/all (should return empty array `[]`)

---

## Step 3 — Import Postman Files

1. Open Postman
2. Click **Import** (top-left)
3. Import BOTH files:
   - `Member2_Offer_Booking.postman_collection.json`
   - `DS_Assignment_Environment.postman_environment.json`
4. In the top-right dropdown, select **"DS Assignment — Local"** environment
5. **Delete or ignore any old "DS Ass" environment** — its empty values were overriding the URLs

---

## Step 4 — Test in Order

Run the requests in this sequence:

### A) Verify categories exist
- `GET all categories` → should return Plumbing (id=1), Carpentry (id=2), etc.

### B) Create a fresh offer
- `POST create listing (Provider)` → note the `offerId` returned (e.g. `offerId: 5`)

### C) Register/verify customer has wallet balance in M1
- Use M1's Postman collection to register customer and add funds first

### D) Create a booking
- `POST create booking (Customer)` → set `"offerId"` to the NEW id from step B
- Expected: 201 Created

### E) Verify booking recorded
- `GET booking history by customer` → set `customerId=1`
- `GET all bookings (Admin)`

---

## Troubleshooting

| Error | Cause | Fix |
|-------|-------|-----|
| `Access denied for user 'root'` | Wrong MySQL password | Update `application.properties` |
| `Payment deduction failed: Connection error` | M1 WildFly not running | Start M1's JBoss server |
| `Payment deduction failed: HTTP 404` | M1 wallet deduct URL different | Check M1's actual endpoint path |
| `Offer unavailable (status=BOOKED)` | Offer already used | Create a NEW offer first |
| `http:///api/...` — Invalid URI | Environment variable empty | Select "DS Assignment — Local" env in Postman |
| Whitelabel Error Page at localhost:8083 | Accessing root URL in browser | Normal — use Postman with full paths |
