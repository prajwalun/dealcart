# DealCart+ Docker Compose Setup

This directory contains the Docker Compose configuration to run the entire DealCart+ stack locally.

## Prerequisites

1. **Docker Desktop** must be installed and running
   - Download from: https://www.docker.com/products/docker-desktop
   - Make sure Docker Desktop is started before running commands

2. **Build all JARs** first (from repo root):
   ```bash
   ./mvnw clean package -DskipTests
   ```

## Quick Start

### 1. Start the Stack

From the repo root:
```bash
docker compose -f infra/docker-compose.yml up -d
```

This will:
- Build Docker images for all services
- Start 6 containers: 2 vendor mocks, vendor-pricing, checkout, edge-gateway, and caddy
- Set up networking between services
- Configure health checks

### 2. Check Status

```bash
docker compose -f infra/docker-compose.yml ps
```

All services should show as "healthy" after 10-20 seconds.

### 3. View Logs

```bash
# All services
docker compose -f infra/docker-compose.yml logs -f

# Specific service
docker compose -f infra/docker-compose.yml logs -f edge-gateway
```

## Testing the Stack

### Test 1: Search with SSE Streaming

```bash
curl -N "http://localhost/api/search?q=headphones" | head -n 5
```

Expected: Stream of vendor quotes as SSE events

### Test 2: Get Best Quote (for JMeter)

```bash
curl "http://localhost/api/quote?productId=sku-123"
```

Expected: Single JSON object with cheapest quote

### Test 3: Start Checkout

```bash
curl -s -X POST http://localhost/api/checkout \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-key-1" \
  -d '{
    "customerId": "customer-123",
    "items": [{
      "productId": "sku-123",
      "quantity": 2,
      "unitPrice": {
        "currencyCode": "USD",
        "amountCents": 1999
      },
      "vendorId": "amz"
    }],
    "shippingAddress": "123 Main St, City, State 12345",
    "paymentMethodId": "pm-card-123"
  }'
```

Expected: JSON with `checkoutId`, `status`, and `message`

### Test 4: Stream Checkout Status

Using the `checkoutId` from Test 3:
```bash
curl -N "http://localhost/api/checkout/<checkoutId>/stream" | head -n 10
```

Expected: Stream of DAG node status updates (reserve, price, tax, pay, confirm)

## Services Overview

| Service | Internal Port | External Port | Description |
|---------|--------------|---------------|-------------|
| vendor-mock-1 | 9101 | 9101 | Mock vendor "amz" |
| vendor-mock-2 | 9102 | 9102 | Mock vendor "bb" |
| vendor-pricing | 9100 | 9100 | Aggregates quotes from vendors |
| checkout | 9200 | 9200 | Handles checkout DAG |
| edge-gateway | 8080 | 8080 | HTTP/SSE gateway |
| caddy | 80/443 | 80/443 | Reverse proxy |

## Access Points

- **Main API**: `http://localhost` (via Caddy)
- **Direct Gateway**: `http://localhost:8080`
- **Health Check**: `http://localhost/actuator/health`
- **Vendor Mock 1**: `localhost:9101`
- **Vendor Mock 2**: `localhost:9102`

## Stopping the Stack

```bash
docker compose -f infra/docker-compose.yml down
```

To also remove volumes:
```bash
docker compose -f infra/docker-compose.yml down -v
```

## Troubleshooting

### Service won't start / shows unhealthy

```bash
# Check logs
docker compose -f infra/docker-compose.yml logs <service-name>

# Restart specific service
docker compose -f infra/docker-compose.yml restart <service-name>
```

### Port already in use

If you see "port is already allocated":
```bash
# Find what's using the port
lsof -i :8080  # or whichever port

# Stop that process or change ports in docker-compose.yml
```

### Rebuild after code changes

```bash
# Rebuild JARs
./mvnw clean package -DskipTests

# Rebuild and restart
docker compose -f infra/docker-compose.yml up -d --build
```

## Configuration

Environment variables can be customized in `docker-compose.yml`:

- **Rate Limiting**: `RATE_LIMIT_QPS` (default: 100)
- **gRPC Timeouts**: `GRPC_DEADLINE_SECONDS` (default: 2)
- **Log Level**: `LOG_LEVEL` (default: INFO)
- **Vendor List**: `VENDORS` in vendor-pricing service

## Architecture

```
[Client/Browser]
       ↓
   [Caddy :80]
       ↓
[edge-gateway :8080] (HTTP/SSE)
       ↓ (gRPC)
       ├─→ [vendor-pricing :9100] ──→ [vendor-mock-1 :9101]
       │                          └──→ [vendor-mock-2 :9102]
       └─→ [checkout :9200]
```

## Next Steps

- Run JMeter load tests against `/api/quote?productId=sku-123`
- Monitor with `docker stats`
- Check Caddy logs for request patterns
- Scale vendor mocks if needed

