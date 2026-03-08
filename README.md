# Food Delivery Platform — Microservices Migration

A production-grade food delivery platform built with Java 21 and Spring Boot 3.x, decomposed from a monolithic architecture into independently deployable microservices communicating via REST (OpenFeign) and asynchronous messaging (RabbitMQ).

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Technology Stack](#technology-stack)
3. [Migration: From Monolith to Microservices](#migration-from-monolith-to-microservices)
4. [Services](#services)
    - [Registry Service](#registry-service)
    - [API Gateway](#api-gateway)
    - [Customer Service](#customer-service)
    - [Restaurant Service](#restaurant-service)
    - [Order Service](#order-service)
    - [Delivery Service](#delivery-service)
5. [Inter-Service Communication](#inter-service-communication)
6. [Circuit Breaker and Resilience](#circuit-breaker-and-resilience)
7. [Authentication and Security](#authentication-and-security)
8. [Asynchronous Messaging](#asynchronous-messaging)
9. [API Reference](#api-reference)
10. [Running the Application](#running-the-application)
11. [End-to-End Testing](#end-to-end-testing)
12. [Fault Tolerance Verification](#fault-tolerance-verification)
13. [Database Schema](#database-schema)
14. [Project Structure](#project-structure)

---

## Architecture Overview

```
                         +---------------------+
                         |   Registry Service  |
                         |   (Eureka Server)   |
                         |     Port: 8761      |
                         +----------+----------+
                                    |
             Registry / Discovery   |
       +-----------+----------+-----+-----+-----------+
       |           |          |           |           |
+------+----+ +----+-----+ +--+-------+ +-+----------+
| API       | | Customer | | Order    | | Restaurant |
| Gateway   | | Service  | | Service  | | Service    |
| Port:8080 | | Port:8081| | Port:8083| | Port:8082  |
+------+----+ +----------+ +----+-----+ +------------+
       |                        |
       |               +--------+--------+
       |               | Delivery        |
       |               | Service         |
       |               | Port: 8084      |
       |               +--------+--------+
       |                        |
       +---------- RabbitMQ ----+
                   Port: 5672
```

All services register with the Eureka Registry Service. The API Gateway is the single entry point for all external traffic — it validates JWT tokens, injects authenticated user headers, and routes requests to downstream services. Inter-service calls use OpenFeign with Resilience4j circuit breakers. Order-to-Delivery communication is event-driven via RabbitMQ.

---

## Technology Stack

| Component           | Technology                                  |
|---------------------|---------------------------------------------|
| Runtime             | Java 21                                     |
| Framework           | Spring Boot 3.x                             |
| Cloud Framework     | Spring Cloud 2024.x                         |
| Service Discovery   | Netflix Eureka                              |
| API Gateway         | Spring Cloud Gateway (WebMVC / Servlet)     |
| Inter-Service Calls | Spring Cloud OpenFeign                      |
| Circuit Breaker     | Resilience4j                                |
| Messaging           | RabbitMQ with Spring AMQP                   |
| Persistence         | Spring Data JPA with PostgreSQL 15          |
| Security            | Spring Security with JWT (JJWT 0.13.x)     |
| Build Tool          | Apache Maven                                |
| Containerisation    | Docker, Docker Compose                      |

---

## Migration: From Monolith to Microservices

### Original Monolith Problems

The starting point was a single Spring Boot application with all four domains sharing one PostgreSQL database.

| # | Problem | Impact |
|---|---------|--------|
| 1 | `OrderService` directly called `RestaurantRepository` and `CustomerRepository` | Cross-domain DB coupling — schema changes in one domain broke others |
| 2 | `DeliveryService.createDelivery()` called synchronously inside `placeOrder()` | A slow delivery operation blocked the entire order response |
| 3 | All entities shared one database with FK constraints across domains | One bad migration could bring down all four features simultaneously |
| 4 | Entire application deployed as one JAR | Scaling order processing during peak hours required scaling everything |
| 5 | A crash in delivery tracking could take down customer authentication | Zero domain isolation |

### Decomposition Strategy

The migration used the **Strangler Fig Pattern** — extract one bounded context at a time rather than a big-bang rewrite.

| Phase | Action |
|-------|--------|
| 1 | Identify bounded contexts using Domain-Driven Design |
| 2 | Assign each context its own database; replace DB joins with Feign calls and data snapshots |
| 3 | Replace synchronous `createDelivery()` with an asynchronous `OrderPlacedEvent` via RabbitMQ |
| 4 | Centralise JWT validation in the API Gateway; inject `X-Authenticated-User` into forwarded requests |

### Bounded Contexts

| Context | Service | Owns |
|---------|---------|------|
| Identity & Customer | customer-service | Registration, JWT issuance, profiles |
| Restaurant & Menu | restaurant-service | Restaurant CRUD, menu items, pricing |
| Order Management | order-service | Order lifecycle, validation, event publishing |
| Delivery | delivery-service | Driver assignment, delivery tracking |

### Architecture Decision Log

| # | Decision | Rationale | Trade-off |
|---|----------|-----------|-----------|
| ADR-01 | Customer Service issues JWTs; Gateway validates them | Auth is part of the customer domain. A separate Auth Service adds a network hop with no domain justification. | JWT secret must be shared between Customer Service and API Gateway. |
| ADR-02 | Restaurant Service calls Customer Service (Feign) for owner validation | Owner ID is required before a restaurant can be saved — synchronous is correct here. | Restaurant creation has a runtime dependency on Customer Service. Mitigated by circuit breaker fallback. |
| ADR-03 | Order records snapshot customer name, restaurant name, item prices | Orders are immutable historical records. If a price changes later, the order must reflect what was agreed at placement. | Data is intentionally duplicated. This is correct domain modelling, not a shortcut. |
| ADR-04 | Delivery assignment is async via RabbitMQ (not a Feign call) | A crashed Delivery Service must not prevent orders from being placed. Queued events are processed on recovery. | Brief delay (< 1s) between order placement and delivery record creation. `GET /deliveries/order/{id}` may return 404 immediately after placement. |
| ADR-05 | Topic exchange over direct exchange in RabbitMQ | Future consumers (e.g., NotificationService) can bind to existing routing keys without modifying Order Service. | Routing key convention must be documented and maintained. |
| ADR-06 | Servlet-based gateway (Spring MVC) over reactive (WebFlux) | All downstream services use Spring MVC. Keeping the full stack on the same blocking threading model avoids complexity. | Lower I/O throughput under extreme concurrency vs a reactive gateway. |
| ADR-07 | Separate PostgreSQL container per service | Separate containers enforce data ownership at the infrastructure level — no shared connection pool, no shared WAL. | Higher resource usage. Justified by true isolation guarantees. |

---

## Services

### Registry Service

| Property | Value |
|----------|-------|
| Port     | 8761  |
| Role     | Eureka Server — service registration and discovery |

All microservices register on startup. Feign clients resolve logical service names (e.g., `CUSTOMER-SERVICE`) to physical addresses through the Eureka registry. Dashboard: `http://localhost:8761`.

---

### API Gateway

| Property | Value |
|----------|-------|
| Port     | 8080  |
| Role     | Single entry point — JWT validation, header injection, request routing |

Validates JWT tokens via `JwtAuthenticationFilter` (extends `OncePerRequestFilter`). On successful validation, extracts the username and injects `X-Authenticated-User` into the forwarded request via `HttpServletRequestWrapper`. Downstream services read this header directly — no JWT library required in any downstream service.

**Route Configuration:**

| Route Pattern         | Target Service     |
|-----------------------|--------------------|
| `/api/auth/**`        | Customer Service   |
| `/api/customers/**`   | Customer Service   |
| `/api/restaurants/**` | Restaurant Service |
| `/api/orders/**`      | Order Service      |
| `/api/deliveries/**`  | Delivery Service   |

**Public Routes (no token required):**

| Path | Methods |
|------|---------|
| `/api/auth/register` | POST |
| `/api/auth/login` | POST |
| `/api/restaurants/**` | GET |

---

### Customer Service

| Property | Value |
|----------|-------|
| Port     | 8081  |
| Database | `customer_db` (PostgreSQL) |
| Role     | Registration, authentication, JWT issuance, profile management |

Handles customer accounts. Issues signed JWT tokens on successful login. Exposes `GET /api/customers/username/{username}` as an internal endpoint used by Restaurant Service and Order Service during Feign calls.

**Key DTOs:**

| DTO | Fields |
|-----|--------|
| `RegisterRequest` | firstName, lastName, email, password, username, phone |
| `LoginRequest` | email, password |
| `AuthResponse` | token, type, username |
| `CustomerResponse` | id, username, firstName, lastName, email, phone, deliveryAddress, city, role |

---

### Restaurant Service

| Property | Value |
|----------|-------|
| Port     | 8082  |
| Database | `restaurant_db` (PostgreSQL) |
| Role     | Restaurant and menu management, ownership validation |

On restaurant creation, calls Customer Service via Feign to resolve the authenticated user's customer ID as `ownerId`. Menu items are stored with their own `available` flag and can be toggled independently.

**Key DTOs:**

| DTO | Fields |
|-----|--------|
| `RestaurantRequest` | name, description, cuisineType, address, city, phone, estimatedDeliveryMinutes |
| `RestaurantResponse` | id, name, cuisineType, address, active, ownerId, estimatedDeliveryMinutes |
| `MenuItemRequest` | name, description, price, category, available |
| `MenuItemResponse` | id, name, price, category, available, restaurantId |

---

### Order Service

| Property | Value |
|----------|-------|
| Port     | 8083  |
| Database | `order_db` (PostgreSQL) |
| Role     | Order lifecycle management, validation orchestration, event publishing |

Central orchestration service for the order placement flow:

1. Validate customer via Customer Service (Feign).
2. Validate restaurant and menu items via Restaurant Service (Feign).
3. Compute pricing; build order with snapshot fields (`customerName`, `restaurantName`, `restaurantAddress`, `itemName`, `unitPrice`).
4. Persist order.
5. Publish `OrderPlacedEvent` to RabbitMQ.

**Key DTOs:**

| DTO | Fields |
|-----|--------|
| `PlaceOrderRequest` | restaurantId, deliveryAddress, specialInstructions, items[ ] |
| `OrderItemRequest` | menuItemId, quantity, specialInstructions |
| `OrderResponse` | id, status, customerId, customerName, restaurantId, restaurantName, items[ ], totalAmount, deliveryFee, estimatedDeliveryTime, createdAt |

---

### Delivery Service

| Property | Value |
|----------|-------|
| Port     | 8084  |
| Database | `delivery_db` (PostgreSQL) |
| Role     | Driver assignment, delivery tracking, event consumption |

Listens for `OrderPlacedEvent` messages to create delivery assignments asynchronously. Assigns a driver from a simulated pool. On cancellation events, marks the corresponding delivery as `FAILED`.

**Key DTOs:**

| DTO | Fields |
|-----|--------|
| `DeliveryResponse` | id, orderId, status, driverName, driverPhone, pickupAddress, deliveryAddress, assignedAt, estimatedDeliveryTime |

---

## Inter-Service Communication

### Synchronous (OpenFeign)

| Caller             | Target             | Purpose                                              |
|--------------------|--------------------|------------------------------------------------------|
| Restaurant Service | Customer Service   | Resolve owner ID on restaurant creation              |
| Order Service      | Customer Service   | Validate customer existence during order placement   |
| Order Service      | Restaurant Service | Validate restaurant status and menu items            |

All Feign clients propagate the `X-Authenticated-User` header via a shared `FeignConfig` request interceptor.

### Asynchronous (RabbitMQ)

| Event                | Producer       | Consumer         | Exchange       | Routing Key      |
|----------------------|----------------|------------------|----------------|------------------|
| `OrderPlacedEvent`   | Order Service  | Delivery Service | `order.exchange` | `order.placed`  |
| `OrderCancelledEvent`| Order Service  | Delivery Service | `order.exchange` | `order.cancelled`|

---

## Circuit Breaker and Resilience

All Feign clients are protected by Resilience4j circuit breakers.

### Configuration

| Parameter | Value |
|-----------|-------|
| `slidingWindowSize` | 10 |
| `minimumNumberOfCalls` | 5 |
| `failureRateThreshold` | 50% |
| `waitDurationInOpenState` | 10s |
| `permittedNumberOfCallsInHalfOpenState` | 3 |
| `automaticTransitionFromOpenToHalfOpenEnabled` | true |
| `timelimiter.timeoutDuration` | 3s |

### Circuit Breaker Instances

| Service            | Instance Name       | Protected Client         |
|--------------------|---------------------|--------------------------|
| Restaurant Service | `customerService`   | `CustomerInterface`      |
| Order Service      | `customerService`   | `CustomerInterface`      |
| Order Service      | `restaurantService` | `RestaurantInterface`    |

### Fallback Behaviour

| Scenario | Behaviour |
|----------|-----------|
| Customer Service DOWN | Login, registration, and order placement rejected with `503`. Clear error message returned. |
| Restaurant Service DOWN | Order placement rejected with `503`. Restaurant browsing (GET) still works independently. |
| Delivery Service DOWN | Order creation succeeds. `OrderPlacedEvent` queues in RabbitMQ. Delivery is assigned when the service recovers. No data is lost. |

### Monitoring Circuit Breaker State

```
GET http://localhost:{port}/actuator/health
GET http://localhost:{port}/actuator/circuitbreakers
GET http://localhost:{port}/actuator/circuitbreakerevents
GET http://localhost:{port}/actuator/metrics/resilience4j.circuitbreaker.state
```

---

## Authentication and Security

### JWT Flow

```
1. Client  -->  POST /api/auth/register or /api/auth/login  -->  API Gateway
2. Gateway validates credentials, signs JWT (HMAC-SHA256), returns token
3. Client includes "Authorization: Bearer <token>" in subsequent requests
4. JwtAuthenticationFilter validates token, extracts username
5. HttpServletRequestWrapper injects "X-Authenticated-User: <username>"
6. Downstream service reads @RequestHeader("X-Authenticated-User") — no JWT library needed
```

### JWT Token Structure

| Claim | Value |
|-------|-------|
| `sub` | username |
| `iat` | issued-at timestamp |
| `exp` | expiry timestamp |
| Algorithm | HMAC-SHA256 |
| Minimum secret length | 32 characters |

### Security Configuration

| Service | Permitted Without Token |
|---------|------------------------|
| API Gateway | `POST /api/auth/**`, `GET /api/restaurants/**` |
| Customer Service | `POST /api/customers/create`, `GET /api/customers/username/{u}` (internal Feign endpoints) |
| All other services | All endpoints require `X-Authenticated-User` header forwarded by gateway |

---

## Asynchronous Messaging

### RabbitMQ Topology

```
order.exchange (TOPIC)
    |
    +-- routing key: order.placed
    |       +---> delivery.order.placed.queue
    |                 Consumed by: DeliveryEventListener
    |
    +-- routing key: order.cancelled
            +---> delivery.order.cancelled.queue
                      Consumed by: DeliveryEventListener
```

| Resource | Value |
|----------|-------|
| Exchange | `order.exchange` (Topic) |
| Exchange durability | `durable = true` |
| Placed Queue | `delivery.order.placed.queue` |
| Cancelled Queue | `delivery.order.cancelled.queue` |
| Queue durability | `durable = true` |
| Message serialisation | JSON (Jackson) |

### OrderPlacedEvent Payload

```json
{
  "orderId": 1,
  "customerId": 1,
  "customerName": "John Doe",
  "restaurantId": 1,
  "restaurantName": "Burger Hub",
  "restaurantAddress": "78 Market Street, Chicago",
  "deliveryAddress": "123 Main Street, Chicago",
  "estimatedDeliveryTime": "2026-03-04T15:32:00"
}
```

### OrderCancelledEvent Payload

```json
{
  "orderId": 1,
  "customerId": 1
}
```

### Event Processing

| Event | Consumer Action |
|-------|----------------|
| `OrderPlacedEvent` | Assigns random driver, creates `Delivery` record with status `ASSIGNED` |
| `OrderCancelledEvent` | Finds delivery by `orderId`, sets status to `FAILED` |

---

## API Reference

All requests go through the API Gateway at `http://localhost:8080`.
Protected endpoints require `Authorization: Bearer <token>`.

### Authentication

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/register` | No | Register a new customer |
| POST | `/api/auth/login` | No | Authenticate and receive JWT |

**Register Request:**
```json
{
  "firstName": "John", "lastName": "Doe",
  "email": "john@example.com", "password": "password123",
  "phone": "0241234567", "username": "johndoe"
}
```

**Login Request / AuthResponse:**
```json
// Request
{ "email": "john@example.com", "password": "password123" }

// Response 200
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "type": "Bearer", "username": "johndoe" }
```

---

### Customers

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/customers/me` | Yes | Get authenticated user's profile |
| PUT | `/api/customers/me` | Yes | Update profile |

---

### Restaurants

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/restaurants` | No | List all restaurants |
| GET | `/api/restaurants/{id}` | No | Get restaurant by ID |
| GET | `/api/restaurants/{id}/menu` | No | Get restaurant menu |
| POST | `/api/restaurants` | Yes | Create a restaurant |
| POST | `/api/restaurants/{id}/menu` | Yes | Add a menu item |

**Create Restaurant Request:**
```json
{
  "name": "Burger Hub", "description": "Gourmet burgers",
  "cuisineType": "American", "address": "78 Market Street",
  "city": "Chicago", "phone": "+1-773-555-4567",
  "estimatedDeliveryMinutes": 20
}
```

**Add Menu Item Request:**
```json
{
  "name": "Classic Burger", "description": "Beef patty with lettuce",
  "price": 45.00, "category": "Burgers", "available": true
}
```

---

### Orders

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/orders` | Yes | Place a new order |
| GET | `/api/orders/{id}` | Yes | Get order by ID |
| GET | `/api/orders/my-orders` | Yes | Get authenticated user's orders |
| GET | `/api/orders/restaurant/{restaurantId}` | Yes | Get orders for a restaurant |
| PATCH | `/api/orders/{id}/status?status={STATUS}` | Yes | Update order status |
| POST | `/api/orders/{id}/cancel` | Yes | Cancel an order |

**Valid Order Statuses:** `PLACED` → `CONFIRMED` → `PREPARING` → `READY_FOR_PICKUP` → `OUT_FOR_DELIVERY` → `DELIVERED`

**Cancel** is only available from `PLACED` or `CONFIRMED`.

**Place Order Request / Response:**
```json
// Request
{
  "restaurantId": 1,
  "deliveryAddress": "123 Main Street, Chicago",
  "specialInstructions": "Ring the doorbell",
  "items": [
    { "menuItemId": 1, "quantity": 2, "specialInstructions": "Extra sauce" },
    { "menuItemId": 2, "quantity": 1 }
  ]
}

// Response 201
{
  "id": 1, "status": "PLACED",
  "customerId": 1, "customerName": "John Doe",
  "restaurantId": 1, "restaurantName": "Burger Hub",
  "items": [
    { "id": 1, "itemName": "Classic Burger", "quantity": 2, "unitPrice": 45.00, "subtotal": 90.00 },
    { "id": 2, "itemName": "Cheese Fries",   "quantity": 1, "unitPrice": 25.00, "subtotal": 25.00 }
  ],
  "totalAmount": 115.00, "deliveryFee": 2.99,
  "estimatedDeliveryTime": "2026-03-04T15:32:00",
  "createdAt": "2026-03-04T15:12:00"
}
```

---

### Deliveries

> Deliveries are created automatically via RabbitMQ when an order is placed. There is no manual creation endpoint.

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/deliveries/{id}` | Yes | Get delivery by ID |
| GET | `/api/deliveries/order/{orderId}` | Yes | Get delivery by order ID |
| PATCH | `/api/deliveries/{id}/status?status={STATUS}` | Yes | Update delivery status |

**Valid Delivery Statuses:** `ASSIGNED` → `PICKED_UP` → `IN_TRANSIT` → `DELIVERED` (or `FAILED` on cancellation)

**DeliveryResponse:**
```json
{
  "id": 1, "orderId": 1, "status": "ASSIGNED",
  "driverName": "Mike Chen", "driverPhone": "+1-555-0103",
  "pickupAddress": "78 Market Street",
  "deliveryAddress": "123 Main Street, Chicago",
  "assignedAt": "2026-03-04T15:12:38",
  "estimatedDeliveryTime": "2026-03-04T15:32:00"
}
```

---

## Running the Application

### Prerequisites

| Requirement | Minimum Version |
|-------------|----------------|
| Java JDK    | 21             |
| Maven       | 3.8            |
| Docker Desktop | Latest      |

### Step 1 — Build All Services

```bash
cd customer-service   && mvn package -DskipTests && cd ..
cd restaurant-service && mvn package -DskipTests && cd ..
cd order-service      && mvn package -DskipTests && cd ..
cd delivery-service   && mvn package -DskipTests && cd ..
cd api-gateway        && mvn package -DskipTests && cd ..
cd registry-service   && mvn package -DskipTests && cd ..
```

### Step 2 — Start in Dependency Order

```bash
docker compose up -d registry-service
sleep 30

docker compose up -d customer-db restaurant-db order-db delivery-db rabbitmq
sleep 20

docker compose up -d customer-service restaurant-service order-service delivery-service
sleep 40

docker compose up -d api-gateway
```

### Step 3 — Verify

| Check | URL | Expected |
|-------|-----|----------|
| Eureka Dashboard | `http://localhost:8761` | 4 services registered as UP |
| RabbitMQ Console | `http://localhost:15672` (queue / queue) | 2 queues visible |
| Gateway Health | `http://localhost:8080/api/restaurants` | `200 OK` with `[]` |

### Container Port Map

| Container | External Port | Purpose |
|-----------|--------------|---------|
| api-gateway | 8080 | API entry point |
| customer-service | 8081 | Customer API |
| restaurant-service | 8082 | Restaurant API |
| order-service | 8083 | Order API |
| delivery-service | 8084 | Delivery API |
| registry-service | 8761 | Eureka dashboard |
| rabbitmq (AMQP) | 5672 | Message broker |
| rabbitmq (Management) | 15672 | RabbitMQ UI |
| customer-db | 5433 | PostgreSQL |
| restaurant-db | 5434 | PostgreSQL |
| order-db | 5435 | PostgreSQL |
| delivery-db | 5436 | PostgreSQL |

### Useful Commands

```bash
# Container status
docker compose ps

# Live logs
docker compose logs -f order-service

# Rebuild a single service after code change
cd order-service && mvn package -DskipTests && cd ..
docker compose stop order-service && docker compose rm -f order-service
docker compose up --build order-service -d

# Stop (data preserved in volumes)
docker compose down

# Stop and delete all data
docker compose down -v
```

---

## End-to-End Testing

Run requests in sequence against `http://localhost:8080`.

### 1. Register and Login

```bash
# Register
POST /api/auth/register
{ "firstName": "John", "lastName": "Doe", "email": "john@example.com",
  "password": "password123", "phone": "0241234567", "username": "johndoe" }

# Login — copy the token from the response
POST /api/auth/login
{ "email": "john@example.com", "password": "password123" }
```

### 2. Create Restaurant and Menu

```bash
POST /api/restaurants           (Authorization: Bearer <token>)
POST /api/restaurants/1/menu    (x3 items)
```

### 3. Browse Without Token (Public Routes)

```bash
GET /api/restaurants              # 200 OK — no token required
GET /api/restaurants/1/menu       # 200 OK — no token required
```

### 4. Place an Order

```bash
POST /api/orders    (Authorization: Bearer <token>)
# Expected: 201 Created with status: "PLACED"
```

### 5. Verify Event-Driven Delivery (Critical)

```bash
# Wait 2–3 seconds after placing the order, then:
GET /api/deliveries/order/1    (Authorization: Bearer <token>)
# Expected: 200 OK with status: "ASSIGNED" and driver details
```

This confirms the full RabbitMQ pipeline: Order Service published `OrderPlacedEvent` → Delivery Service consumed it → delivery record created automatically, with no direct call between the two services.

### 6. Progress Statuses

```bash
PATCH /api/orders/1/status?status=CONFIRMED
PATCH /api/orders/1/status?status=PREPARING
PATCH /api/orders/1/status?status=READY_FOR_PICKUP
PATCH /api/orders/1/status?status=OUT_FOR_DELIVERY
PATCH /api/orders/1/status?status=DELIVERED

PATCH /api/deliveries/1/status?status=PICKED_UP
PATCH /api/deliveries/1/status?status=IN_TRANSIT
PATCH /api/deliveries/1/status?status=DELIVERED
```

### 7. Test Cancellation Event Flow

```bash
POST /api/orders         # Place a second order → id: 2
POST /api/orders/2/cancel

# Wait 2–3 seconds, then:
GET /api/deliveries/order/2
# Expected: status: "FAILED" — set by OrderCancelledEvent consumer
```

---

## Fault Tolerance Verification

### Delivery Service Down — Orders Still Succeed

```bash
docker compose stop delivery-service

POST http://localhost:8080/api/orders   # Expected: 201 Created (order saves, event queues)

docker compose start delivery-service
sleep 5

GET http://localhost:8080/api/deliveries/order/{id}   # Expected: 200 ASSIGNED (queued event processed)
```

### Customer Service Down — Browsing Still Works

```bash
docker compose stop customer-service

GET http://localhost:8080/api/restaurants   # Expected: 200 OK (no dependency)
POST http://localhost:8080/api/auth/login   # Expected: 503 (fails gracefully, no crash)

docker compose start customer-service
```

---

## Database Schema

The application uses four separate PostgreSQL databases. No cross-database foreign key constraints exist. Cross-domain references are stored as plain `BIGINT` ID fields.

| Database | Service | Tables |
|----------|---------|--------|
| `customer_db` | Customer Service | `customers` |
| `restaurant_db` | Restaurant Service | `restaurants`, `menu_items` |
| `order_db` | Order Service | `orders`, `order_items` |
| `delivery_db` | Delivery Service | `deliveries` |

Schemas are managed by Hibernate (`ddl-auto: create` in development, `validate` in production). Data persists in named Docker volumes across container restarts.

### Key Schema Notes

| Table | Notable Columns |
|-------|----------------|
| `orders` | `customer_name`, `restaurant_name`, `restaurant_address` — snapshots taken at order time |
| `order_items` | `item_name`, `unit_price` — snapshots taken at order time |
| `restaurants` | `owner_id` — logical reference to `customer_db.customers.id` |
| `deliveries` | `order_id` — logical reference to `order_db.orders.id` |

---

## Project Structure

```
food-delivery-platform/
├── docker-compose.yml
├── README.md
│
├── api-gateway/
│   ├── src/main/java/api_gateway/
│   │   ├── ApiGatewayApplication.java
│   │   └── security/
│   │       ├── GatewayConfig.java              # Routes + RestClient proxy
│   │       ├── JwtAuthenticationFilter.java    # OncePerRequestFilter
│   │       └── JwtUtil.java
│   └── src/main/resources/
│       ├── application.properties
│       └── application-docker.properties
│
├── registry-service/
│   └── src/main/resources/application.properties
│
├── customer-service/
│   ├── src/main/java/customer_service/
│   │   ├── controller/   AuthController.java, CustomerController.java
│   │   ├── service/      AuthService.java, CustomerService.java
│   │   ├── entity/       Customer.java
│   │   ├── repository/   CustomerRepository.java
│   │   ├── dto/
│   │   └── security/     JwtUtil.java, SecurityConfig.java
│   └── src/main/resources/
│       ├── application.properties
│       └── application-docker.properties
│
├── restaurant-service/
│   ├── src/main/java/restaurant_service/
│   │   ├── controller/   RestaurantController.java
│   │   ├── service/      RestaurantService.java
│   │   ├── entity/       Restaurant.java, MenuItem.java
│   │   ├── feign/        CustomerInterface.java
│   │   └── fallback/     CustomerClientFallback.java
│   └── src/main/resources/
│       ├── application.properties
│       └── application-docker.properties
│
├── order-service/
│   ├── src/main/java/order_service/
│   │   ├── controller/   OrderController.java
│   │   ├── service/      OrderService.java
│   │   ├── entity/       Order.java, OrderItem.java
│   │   ├── feign/        CustomerInterface.java, RestaurantInterface.java
│   │   ├── fallback/     CustomerClientFallback.java, RestaurantClientFallback.java
│   │   ├── dto/events/   OrderPlacedEvent.java, OrderCancelledEvent.java
│   │   └── config/       RabbitMQConfig.java
│   └── src/main/resources/
│       ├── application.properties
│       └── application-docker.properties
│
└── delivery-service/
    ├── src/main/java/delivery_service/
    │   ├── controller/   DeliveryController.java
    │   ├── service/      DeliveryService.java, DeliveryEventListener.java
    │   ├── entity/       Delivery.java
    │   ├── dto/events/   OrderPlacedEvent.java, OrderCancelledEvent.java
    │   └── config/       RabbitMQConfig.java
    └── src/main/resources/
        ├── application.properties
        └── application-docker.properties
```