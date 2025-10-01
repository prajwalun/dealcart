# DealCart+ 🛒

A full-stack microservices demo showcasing **Java Spring Boot**, **gRPC**, **Protocol Buffers**, **async promise-graph checkout**, and **real-time SSE streaming**.

## Architecture Overview

```
┌─────────────┐
│   Next.js   │  (Port 3000, served via Caddy)
│   Web UI    │
└──────┬──────┘
       │ HTTP/SSE
┌──────▼──────┐
│    Caddy    │  (Port 80)
│   Proxy     │
└──────┬──────┘
       │
┌──────▼──────────┐
│  edge-gateway   │  (Port 8080) Spring Boot HTTP/SSE ↔ gRPC bridge
└────┬─────┬──────┘
     │     │ gRPC
     │     └──────────────┐
     │                    │
┌────▼─────────┐    ┌────▼────────┐
│vendor-pricing│    │  checkout   │  (Port 9200) Promise-graph DAG + SAGA
│  (Port 9100) │    └─────────────┘
└────┬─────────┘
     │ gRPC fan-out
     ├──────────────────┐
┌────▼────┐      ┌──────▼────┐
│vendor-  │      │ vendor-   │
│mock-1   │      │ mock-2    │
│(9101)   │      │ (9102)    │
│ "amz"   │      │  "bb"     │
└─────────┘      └───────────┘
```

## Tech Stack

### Backend
- **Java 17** with Maven
- **Spring Boot 3.3.3** (edge-gateway only)
- **gRPC 1.66.0** with Netty
- **Protocol Buffers 3.25.4**
- **CompletableFuture** for async orchestration
- **SLF4J** logging

### Frontend
- **Next.js 15** (App Router)
- **TypeScript**
- **Tailwind CSS**
- **shadcn/ui** components
- **Zustand** state management
- **EventSource** for SSE

### Infrastructure
- **Docker** + **Docker Compose**
- **Caddy** reverse proxy
- **GitHub Actions** CI/CD (coming)
- **JMeter** load tests (coming)

## Quick Start

### Prerequisites

- **Docker Desktop** installed and running
- **Java 17+** and **Maven 3.9+** (or use `./mvnw`)
- **Node.js 20+** and **pnpm** (for UI development)

### Option 1: Full Stack with Docker (Recommended)

```bash
# Clone the repo
git clone https://github.com/prajwalun/dealcart.git
cd dealcart

# Run setup script
./setup-local.sh

# Or manually:
./mvnw clean package -DskipTests
docker compose -f infra/docker-compose.yml up -d
```

Visit **http://localhost** - UI and API are both accessible!

### Option 2: Local Development (UI + Backend Separately)

**Terminal 1 - Backend**:
```bash
# Build services
./mvnw clean package -DskipTests

# Start backend services only
docker compose -f infra/docker-compose.yml up -d vendor-mock-1 vendor-mock-2 vendor-pricing checkout edge-gateway
```

**Terminal 2 - UI**:
```bash
cd web-ui
echo "NEXT_PUBLIC_API_BASE_URL=http://localhost:8080" > .env.local
pnpm install
pnpm dev
```

Visit **http://localhost:3000**

## Project Structure

```
dealcart/
├── proto/                    # Protocol Buffer definitions
│   └── src/main/proto/       # .proto files
├── vendor-mock/              # Mock vendor backend (gRPC)
├── vendor-pricing/           # Quote aggregator (gRPC server-stream)
├── checkout/                 # Checkout DAG + SAGA (gRPC)
├── edge-gateway/             # HTTP/SSE bridge (Spring Boot)
├── web-ui/                   # Next.js frontend
├── infra/                    # Docker Compose + Caddy
│   ├── docker-compose.yml
│   └── Caddyfile
├── pom.xml                   # Parent Maven POM
└── setup-local.sh            # Quick setup script
```

## Services

| Service | Technology | Port | Description |
|---------|-----------|------|-------------|
| **vendor-mock** | Netty gRPC | 9101, 9102 | Simulates vendor APIs with latency |
| **vendor-pricing** | Netty gRPC | 9100 | Aggregates quotes, streams results |
| **checkout** | Netty gRPC | 9200 | DAG orchestration with SAGA rollbacks |
| **edge-gateway** | Spring Boot | 8080 | HTTP/SSE ↔ gRPC bridge |
| **web-ui** | Next.js | 3000 | React UI with real-time updates |
| **caddy** | Caddy | 80, 443 | Reverse proxy |

## Key Features

### Promise-Graph Checkout
- **DAG Flow**: Reserve → [Price, Tax] → Pay → Confirm
- **Retries**: Payment retries with exponential backoff
- **SAGA**: Automatic compensations (Release, Void) on failure
- **Streaming**: Real-time status updates via SSE

### Real-Time Quotes
- **gRPC Streaming**: Server-streams quotes from multiple vendors
- **SSE Bridge**: Converted to Server-Sent Events for web clients
- **Parallel Fan-out**: CompletableFuture for concurrent vendor calls
- **Deadlines**: 1.5s per vendor to prevent tail latency

### Production Features
- **Rate Limiting**: Token bucket (100 QPS default)
- **Request Tracing**: UUID propagation via gRPC metadata
- **Health Checks**: All services monitored
- **Graceful Shutdown**: Proper resource cleanup
- **Error Handling**: Comprehensive error boundaries

## API Endpoints

### Search & Quotes
- `GET /api/search?q={query}` - SSE stream of quotes
- `GET /api/quote?productId={id}&mode=best` - Single best quote (for JMeter)

### Checkout
- `POST /api/checkout` - Start checkout (requires Idempotency-Key)
- `GET /api/checkout/{id}/stream` - SSE stream of DAG status

### Health
- `GET /actuator/health` - Spring Boot health check

## Testing

### Manual Testing

**Search for products**:
```bash
curl -N "http://localhost/api/search?q=headphones" | head -n 5
```

**Get best quote**:
```bash
curl "http://localhost/api/quote?productId=sku-123&mode=best"
```

**Start checkout**:
```bash
curl -X POST http://localhost/api/checkout \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "customerId": "cust-123",
    "items": [{
      "productId": "sku-123",
      "quantity": 2,
      "unitPrice": {"currencyCode": "USD", "amountCents": 1999},
      "vendorId": "amz"
    }],
    "shippingAddress": "123 Main St",
    "paymentMethodId": "pm-card-123"
  }'
```

**Stream checkout status**:
```bash
curl -N "http://localhost/api/checkout/{checkoutId}/stream"
```

### Load Testing (Coming)

JMeter test plan targeting `/api/quote?productId=sku-123&mode=best`

## Development Workflow

### Making Backend Changes

```bash
# 1. Edit code
# 2. Rebuild
./mvnw clean package -DskipTests -pl {module} -am

# 3. Restart specific service
docker compose -f infra/docker-compose.yml up -d --build {service}
```

### Making Frontend Changes

```bash
cd web-ui

# Dev mode (hot reload)
pnpm dev

# Or rebuild in Docker
docker compose -f infra/docker-compose.yml up -d --build web-ui
```

## Monitoring

```bash
# View all logs
docker compose -f infra/docker-compose.yml logs -f

# Specific service
docker compose -f infra/docker-compose.yml logs -f edge-gateway

# Service status
docker compose -f infra/docker-compose.yml ps

# Resource usage
docker stats
```

## Deployment (EC2)

Coming in next steps:
- GitHub Actions CI/CD
- EC2 deployment with Docker Compose
- S3 for static Next.js export (optional)
- Route 53 + ACM for custom domain

## Troubleshooting

**Services won't start**:
```bash
# Check logs
docker compose -f infra/docker-compose.yml logs {service}

# Rebuild
docker compose -f infra/docker-compose.yml up -d --build
```

**Port conflicts**:
```bash
# Find what's using the port
lsof -i :8080

# Kill it or change port in docker-compose.yml
```

**UI can't connect to API**:
- Check `NEXT_PUBLIC_API_BASE_URL` in web-ui/.env.local
- Verify edge-gateway is healthy: `curl http://localhost:8080/actuator/health`
- Check browser console for CORS errors

## License

MIT (Portfolio/Demo Project)

## Author

Built as a portfolio project showcasing modern microservices architecture.

