# DealCart+ Load Testing with JMeter

This directory contains JMeter test plans for load testing DealCart+ at scale.

## Prerequisites

### Install JMeter

**On macOS:**
```bash
brew install jmeter
```

**On Linux:**
```bash
# Download JMeter 5.6.3
wget https://dlcdn.apache.org//jmeter/binaries/apache-jmeter-5.6.3.tgz
tar -xzf apache-jmeter-5.6.3.tgz
export PATH=$PATH:$PWD/apache-jmeter-5.6.3/bin
```

**Verify installation:**
```bash
jmeter -v
# Should show: Version 5.6.3 or higher
```

---

## Test Plan Overview

**File**: `dealcart.jmx`

**Configuration:**
- **Target**: `GET /api/quote?productId={id}&mode=best`
- **Threads**: 50 concurrent users
- **Ramp-up**: 60 seconds (gradual load increase)
- **Duration**: 600 seconds (10 minutes)
- **Target RPS**: ~200 requests per second
- **Products**: 30 different SKUs (rotated from products.csv)

**What it tests:**
- Quote API latency under sustained load
- Vendor-pricing autoscaling behavior
- System stability over 10 minutes
- P50, P95, P99 latency percentiles

---

## Step 10: Baseline vs Autoscaled

### Test 1: Baseline (Fixed 8 threads)

Lock the thread pool to measure performance without autoscaling:

```bash
# 1. Update docker-compose.yml for baseline
cd /Users/prajwal/Documents/dealcart
```

Edit `infra/docker-compose.yml` - set vendor-pricing to:
```yaml
environment:
  - ADAPTIVE_MIN=8
  - ADAPTIVE_MAX=8  # <-- Lock at 8 (no scaling)
```

```bash
# 2. Restart vendor-pricing
docker compose -f infra/docker-compose.yml up -d vendor-pricing

# 3. Wait for healthy
sleep 10

# 4. Run baseline test
cd /Users/prajwal/Documents/dealcart
jmeter -n -t loadtest/dealcart.jmx \
  -l loadtest/baseline.jtl \
  -e -o loadtest/results-baseline

# 5. Monitor during test (in another terminal)
docker compose -f infra/docker-compose.yml logs -f vendor-pricing | grep snapshot
```

---

### Test 2: Autoscaled (8-64 threads)

Restore autoscaling to measure adaptive performance:

```bash
# 1. Update docker-compose.yml for autoscaling
```

Edit `infra/docker-compose.yml` - set vendor-pricing to:
```yaml
environment:
  - ADAPTIVE_MIN=8
  - ADAPTIVE_MAX=64  # <-- Restore autoscaling
```

```bash
# 2. Restart vendor-pricing
docker compose -f infra/docker-compose.yml up -d vendor-pricing

# 3. Wait for healthy
sleep 10

# 4. Run autoscaled test
cd /Users/prajwal/Documents/dealcart
jmeter -n -t loadtest/dealcart.jmx \
  -l loadtest/auto.jtl \
  -e -o loadtest/results-autoscaled

# 5. Monitor scaling (in another terminal)
docker compose -f infra/docker-compose.yml logs -f vendor-pricing | grep -E "snapshot|SCALE"
```

---

## Expected Results

### Baseline (Fixed 8 threads)
- **P95 latency**: 400-600ms (degraded under load)
- **P99 latency**: 800-1200ms
- **Throughput**: ~150-180 RPS (below target)
- **Pool size**: Fixed at 8 threads
- **Queue depth**: Grows significantly under load

### Autoscaled (8-64 threads)
- **P95 latency**: ≤250ms (target achieved)
- **P99 latency**: ≤500ms
- **Throughput**: ~200 RPS (target achieved)
- **Pool size**: Scales 8→16→24→32 as needed
- **Queue depth**: Stays low (efficient)

### Success Criteria
✅ Autoscaled P95 < Baseline P95
✅ Autoscaled P99 < Baseline P99
✅ Autoscaled throughput ≥ Baseline throughput
✅ Both tests: >99% success rate

---

## Viewing Results

### HTML Reports

**Baseline:**
```bash
open loadtest/results-baseline/index.html
```

**Autoscaled:**
```bash
open loadtest/results-autoscaled/index.html
```

### Key Metrics to Compare

1. **Response Time Percentiles**
   - Look for: "Response Times Percentiles" graph
   - Compare: P90, P95, P99 lines

2. **Throughput**
   - Look for: "Transactions per Second" graph
   - Compare: Average TPS

3. **Error Rate**
   - Look for: Summary table
   - Should be: <1% errors

4. **Response Time Over Time**
   - Look for: "Response Time Over Time" graph
   - Baseline: Should show degradation
   - Autoscaled: Should stay stable

---

## Quick Commands

### Clean up old results:
```bash
rm -rf loadtest/results-* loadtest/*.jtl
```

### Run both tests back-to-back:
```bash
./loadtest/run-comparison.sh
```

### View autoscaler logs:
```bash
docker compose -f infra/docker-compose.yml logs -f vendor-pricing | grep -E "snapshot|SCALE|p95"
```

### Monitor system during test:
```bash
# Terminal 1: Autoscaler
docker compose -f infra/docker-compose.yml logs -f vendor-pricing | grep snapshot

# Terminal 2: JMeter progress
tail -f jmeter.log

# Terminal 3: Container stats
docker stats vendor-pricing edge-gateway
```

---

## Troubleshooting

### JMeter says "Connection refused"
```bash
# Check services are running
docker compose -f infra/docker-compose.yml ps

# Test manually
curl "http://localhost/api/quote?productId=sku-laptop&mode=best"
```

### Test completes too quickly
- Check duration in dealcart.jmx (should be 600 seconds)
- Verify Thread Group > Scheduler is enabled

### Low throughput
- Increase thread count (ThreadGroup.num_threads)
- Check ConstantThroughputTimer (should be 12000 samples/min = 200/sec)

### High error rate
- Check docker logs for backend errors
- Verify all services are healthy
- Increase timeout in HTTP Sampler (currently 3000ms)

---

## Files

- **dealcart.jmx**: Main JMeter test plan
- **products.csv**: Product IDs for testing (30 SKUs)
- **baseline.jtl**: Results from fixed-thread test
- **auto.jtl**: Results from autoscaled test
- **results-baseline/**: HTML report for baseline
- **results-autoscaled/**: HTML report for autoscaled
- **README.md**: This file

---

## Architecture Under Test

```
JMeter (50 threads, 200 RPS)
    ↓
Caddy :80
    ↓
edge-gateway :8080
    ↓ gRPC
vendor-pricing :9100 (AUTOSCALES 8-64 threads)
    ↓ parallel fan-out
vendor-mock-1 :9101 (amz) + vendor-mock-2 :9102 (bb)
```

**What's being tested:**
- Adaptive thread pool scaling in vendor-pricing
- gRPC call latency aggregation
- SSE-to-HTTP bridge in edge-gateway
- End-to-end quote retrieval flow

---

## Next Steps

After comparing baseline vs autoscaled:

1. **Analyze HTML reports** - Compare P95/P99
2. **Check autoscaler logs** - Verify scaling happened
3. **Document findings** - Add to project README
4. **Tune parameters** - Adjust TARGET_P95_MS if needed
5. **Scale further** - Try 500 RPS, 1000 RPS

**Ready for production-scale load testing!** 🚀

