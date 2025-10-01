# Horizontal Scaling Plan for DealCart+

This document outlines how to add horizontal scaling to DealCart+ to handle traffic spikes.

## Current Architecture (Vertical Scaling Only)

```
Client
  ↓
Caddy :80
  ↓
edge-gateway :8080 (1 instance)
  ↓
vendor-pricing :9100 (1 instance, autoscales 8-64 threads)
  ↓
vendor-mock-1, vendor-mock-2
```

**Handles:** ~75 RPS sustained with 1 instance

---

## Proposed Architecture (Horizontal + Vertical Scaling)

```
Client
  ↓
Caddy :80 (Load Balancer)
  ↓
├─ edge-gateway-1 :8080 ──┐
├─ edge-gateway-2 :8081 ──┼─ Round-robin
└─ edge-gateway-3 :8082 ──┘
       ↓
├─ vendor-pricing-1 :9100 ──┐
├─ vendor-pricing-2 :9101 ──┼─ Load balanced
└─ vendor-pricing-3 :9102 ──┘
   (each autoscales 8-64 threads)
       ↓
├─ vendor-mock-1
├─ vendor-mock-2
├─ vendor-mock-3
└─ vendor-mock-4
```

**Handles:** ~500+ RPS with multiple instances

---

## Implementation Steps

### Step 1: Docker Compose Scaling

**File:** `infra/docker-compose.yml`

Remove container_name and add `deploy.replicas`:

```yaml
vendor-pricing:
  build:
    context: ../vendor-pricing
  deploy:
    replicas: 3  # Run 3 instances
  environment:
    - PORT=9100
    # ... autoscaling config ...
```

**Scale dynamically:**
```bash
# Scale up to 5 instances
docker compose -f infra/docker-compose.yml up -d --scale vendor-pricing=5

# Scale down to 2 instances
docker compose -f infra/docker-compose.yml up -d --scale vendor-pricing=2
```

---

### Step 2: Load Balancer Configuration

**Update:** `infra/Caddyfile`

```caddyfile
:80 {
    # API routes go to edge-gateway with load balancing
    route /api/* {
        # Round-robin load balancing across multiple edge-gateway instances
        reverse_proxy edge-gateway-1:8080 edge-gateway-2:8081 edge-gateway-3:8082 {
            lb_policy round_robin
            health_uri /actuator/health
            health_interval 10s
            health_timeout 5s
        }
    }
    
    # UI routes
    reverse_proxy /* web-ui:3000
}
```

---

### Step 3: Service Discovery (Advanced)

**Option A: Docker Compose Service Discovery** (Simple)
```yaml
# Docker automatically load-balances across replicas of the same service
# edge-gateway connects to "vendor-pricing:9100"
# Docker DNS round-robins across all vendor-pricing instances
```

**Option B: Consul/etcd** (Production)
- Services register themselves on startup
- Clients discover healthy instances dynamically
- Automatic deregistration on failure

---

### Step 4: Metrics & Observability

**Add Prometheus + Grafana:**

```yaml
# docker-compose.yml additions

prometheus:
  image: prom/prometheus:latest
  ports:
    - "9090:9090"
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml

grafana:
  image: grafana/grafana:latest
  ports:
    - "3001:3000"
  depends_on:
    - prometheus
```

**Metrics to track:**
- Request rate per instance
- Active thread count per instance
- P95/P99 latency per instance
- Error rate per instance
- CPU/Memory per instance

---

## Traffic Patterns to Test

### Scenario 1: Normal Day (Current Test) ✅
```
Load: 50-100 concurrent users
Duration: 10-30 minutes
Instances: 1 vendor-pricing, 1 edge-gateway
Result: 74 RPS, P95 ~836ms
```

### Scenario 2: Flash Sale Spike
```
Load: 500 concurrent users (10x spike)
Duration: 5 minutes
Instances: Scale up to 3-5 instances
Target: 500+ RPS, P95 <500ms
```

### Scenario 3: Black Friday Sustained
```
Load: 200-300 concurrent users
Duration: 60 minutes
Instances: 3-4 instances (horizontal)
Target: 300+ RPS sustained, P95 <500ms
```

---

## Portfolio Presentation

### Current Setup (What You Have Now):
```
"DealCart+ handles 100K requests with 99.8% success rate
using adaptive thread pools that automatically scale from
8 to 64 threads based on P95 latency metrics."
```

### With Horizontal Scaling:
```
"DealCart+ uses both vertical (thread scaling) and horizontal
(instance scaling) autoscaling. Under load, it automatically:
- Scales threads: 8→64 per instance (vertical)
- Scales instances: 1→5 replicas (horizontal)
- Handles 500+ RPS with sub-500ms P95 latency
- Self-heals by removing unhealthy instances"
```

---

## Quick Wins for Your Portfolio

### What to Highlight NOW (No Code Changes):
1. ✅ **Microservices architecture** (gRPC, Spring Boot, Java 17)
2. ✅ **Adaptive thread pools** (custom autoscaling implementation)
3. ✅ **Load tested at scale** (100K requests, 99.8% success)
4. ✅ **Real-time SSE streaming** (Server-Sent Events)
5. ✅ **SAGA pattern** (distributed transactions with compensations)
6. ✅ **Promise-graph DAG** (checkout orchestration)
7. ✅ **Docker Compose** (6 services orchestrated)
8. ✅ **Modern UI** (Next.js, TypeScript, Tailwind)

### What to ADD for "Wow Factor":
1. 🔥 **Horizontal scaling** (Docker Compose replicas)
2. 🔥 **Metrics dashboard** (Grafana showing autoscaling in action)
3. 🔥 **Load balancer** (Caddy with health checks)
4. 🔥 **Spike test** (500 concurrent users, watch it scale)

---

## My Recommendation:

**For a portfolio project, you have TWO options:**

### **Option 1: Keep it Simple (Current Setup)** ⭐️ RECOMMENDED
- What you have is already impressive
- Focus on explaining the architecture well
- Show the JMeter results (100K requests, autoscaling logs)
- Demo the UI with real-time quotes and checkout
- **Time investment:** Done! Just document it well

### **Option 2: Add Horizontal Scaling** ⭐️⭐️⭐️ ADVANCED
- Add Docker Compose replicas (3-5 instances)
- Add load balancer with health checks
- Run spike test with 500 concurrent users
- Show Grafana dashboard with live metrics
- **Time investment:** 2-4 hours more work

---

## **Which Would You Like?**

**A) Document current results and move on** (smart choice - you have great results)

**B) Add horizontal scaling + metrics dashboard** (more impressive, takes longer)

**C) Just create a higher-concurrency test** (500 threads, shows spike handling)

Let me know and I'll help you implement it! 🚀
