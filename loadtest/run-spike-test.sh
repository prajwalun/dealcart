#!/bin/bash

# DealCart+ Spike Test - Demonstrates Horizontal + Vertical Scaling
# Simulates Black Friday traffic spike: low → high → low

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================="
echo "DealCart+ Spike Test"
echo "Simulates Flash Sale Traffic Spike"
echo "========================================="
echo ""

# Check JMeter
if ! command -v jmeter &> /dev/null; then
    echo "❌ JMeter not found! Install with: brew install jmeter"
    exit 1
fi

echo "✅ JMeter ready"
echo ""

cd "$PROJECT_ROOT"

# Check services
if ! docker compose -f infra/docker-compose.yml ps | grep -q "vendor-pricing.*Up"; then
    echo "❌ Services not running! Start with: docker compose -f infra/docker-compose.yml up -d"
    exit 1
fi

echo "✅ Services running"
echo ""

# Clean old results
rm -rf "$SCRIPT_DIR/results-spike" "$SCRIPT_DIR/spike.jtl"

echo "========================================="
echo "SPIKE TEST: 500 Concurrent Users"
echo "========================================="
echo ""
echo "Configuration:"
echo "  - 500 concurrent threads (simulates flash sale)"
echo "  - 50 loops per thread = 25,000 total requests"
echo "  - 60 second ramp-up (0 → 500 users)"
echo "  - Expected duration: ~3-4 minutes"
echo ""
echo "What to watch:"
echo "  - Autoscaler scaling threads (8→16→32→48→64)"
echo "  - Multiple instances sharing load"
echo "  - Latency staying stable despite spike"
echo ""

# Show current instance count
echo "Current deployment:"
docker compose -f infra/docker-compose.yml ps --format "table {{.Service}}\t{{.Name}}\t{{.Status}}" | grep -E "vendor-pricing|edge-gateway|Service"
echo ""

# Count instances
PRICING_COUNT=$(docker compose -f infra/docker-compose.yml ps vendor-pricing -q | wc -l | tr -d ' ')
GATEWAY_COUNT=$(docker compose -f infra/docker-compose.yml ps edge-gateway -q | wc -l | tr -d ' ')

echo "📊 Running with:"
echo "  vendor-pricing: $PRICING_COUNT instances (each can scale 8-64 threads)"
echo "  edge-gateway: $GATEWAY_COUNT instances"
echo "  Total capacity: ~$((PRICING_COUNT * 100)) RPS (estimated)"
echo ""

read -p "Press Enter to start spike test..."
echo ""

# Start background monitoring
echo "Starting background monitors..."
echo "  Logs: infra/spike-test-logs.txt"
echo ""

# Monitor autoscaler in background
docker compose -f infra/docker-compose.yml logs -f vendor-pricing 2>&1 | grep -E "snapshot|SCALE" > infra/spike-test-logs.txt &
MONITOR_PID=$!

# Run spike test
echo "🚀 Launching spike test..."
cd "$SCRIPT_DIR"

START_TIME=$(date +%s)

jmeter -n -t dealcart-spike.jmx \
  -l spike.jtl \
  -e -o results-spike \
  -Juser.dir="$PROJECT_ROOT" \
  -Jjmeter.reportgenerator.overall_granularity=1000 2>&1 | grep summary

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Stop monitoring
kill $MONITOR_PID 2>/dev/null || true

echo ""
echo "✅ Spike test complete in ${DURATION}s!"
echo ""

# Extract metrics
echo "Extracting metrics..."
SPIKE_AVG=$(awk -F',' 'NR>1 {sum+=$2; count++} END {if(count>0) print int(sum/count); else print 0}' spike.jtl)
SPIKE_P50=$(awk -F',' 'NR>1 {print $2}' spike.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{if(c>0) print a[int(c*0.50)]; else print 0}')
SPIKE_P95=$(awk -F',' 'NR>1 {print $2}' spike.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{if(c>0) print a[int(c*0.95)]; else print 0}')
SPIKE_P99=$(awk -F',' 'NR>1 {print $2}' spike.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{if(c>0) print a[int(c*0.99)]; else print 0}')
SPIKE_COUNT=$(awk -F',' 'NR>1 {count++} END {print count}' spike.jtl)
SPIKE_ERRORS=$(awk -F',' 'NR>1 && $8=="false" {errors++} END {print errors+0}' spike.jtl)
SPIKE_ERROR_RATE=$(echo "scale=2; $SPIKE_ERRORS / $SPIKE_COUNT * 100" | bc)
SPIKE_TPS=$(echo "scale=1; $SPIKE_COUNT / $DURATION" | bc)

echo ""
echo "========================================="
echo "SPIKE TEST RESULTS"
echo "========================================="
echo ""
echo "Load Profile:"
echo "  Peak Concurrency: 500 users"
echo "  Total Requests:   $SPIKE_COUNT"
echo "  Duration:         ${DURATION}s"
echo "  Throughput:       ${SPIKE_TPS} TPS"
echo ""
echo "Latency Metrics:"
echo "  Average:  ${SPIKE_AVG}ms"
echo "  P50:      ${SPIKE_P50}ms"
echo "  P95:      ${SPIKE_P95}ms ⭐"
echo "  P99:      ${SPIKE_P99}ms"
echo ""
echo "Reliability:"
echo "  Errors:      ${SPIKE_ERRORS}"
echo "  Error Rate:  ${SPIKE_ERROR_RATE}%"
echo "  Success Rate: $(echo "scale=2; 100 - $SPIKE_ERROR_RATE" | bc)%"
echo ""

# Show autoscaling behavior
echo "========================================="
echo "AUTOSCALING BEHAVIOR"
echo "========================================="
echo ""
echo "Thread Scaling (per instance):"
cat infra/spike-test-logs.txt | grep "SCALE" | head -10 || echo "  No scaling events (load was within capacity)"
echo ""
echo "Periodic Snapshots (last 5):"
cat infra/spike-test-logs.txt | grep "snapshot" | tail -5
echo ""

# Acceptance criteria
echo "========================================="
echo "ACCEPTANCE CRITERIA"
echo "========================================="
echo ""

if (( $(echo "$SPIKE_P95 <= 1000" | bc -l) )); then
    echo "✅ P95 ≤ 1000ms under spike: PASS ($SPIKE_P95 ms)"
else
    echo "❌ P95 ≤ 1000ms under spike: FAIL ($SPIKE_P95 ms)"
fi

if (( $(echo "$SPIKE_ERROR_RATE < 2.0" | bc -l) )); then
    echo "✅ Error Rate < 2% under spike: PASS (${SPIKE_ERROR_RATE}%)"
else
    echo "❌ Error Rate < 2% under spike: FAIL (${SPIKE_ERROR_RATE}%)"
fi

if (( $(echo "$SPIKE_COUNT > 20000" | bc -l) )); then
    echo "✅ Processed 20K+ requests: PASS ($SPIKE_COUNT)"
else
    echo "❌ Processed 20K+ requests: FAIL ($SPIKE_COUNT)"
fi

echo ""
echo "========================================="
echo "View HTML report:"
echo "  open $SCRIPT_DIR/results-spike/index.html"
echo ""
echo "View autoscaling logs:"
echo "  cat $PROJECT_ROOT/infra/spike-test-logs.txt"
echo "========================================="

