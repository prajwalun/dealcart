#!/bin/bash

# DealCart+ Flash Sale Test - Realistic Burst Load
# Simulates 500 users hitting the site simultaneously during a flash sale

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================="
echo "DealCart+ Flash Sale Test"
echo "Realistic Burst Load Simulation"
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
rm -rf "$SCRIPT_DIR/results-flash-sale" "$SCRIPT_DIR/flash-sale.jtl"

echo "========================================="
echo "FLASH SALE SIMULATION"
echo "========================================="
echo ""
echo "Scenario: Black Friday Flash Sale"
echo "  - 500 users hit the site simultaneously"
echo "  - Each user makes 10 requests over 2 minutes"
echo "  - Total: 5,000 requests in 2 minutes"
echo "  - Expected RPS: ~250-300 RPS (burst)"
echo ""
echo "Current System Capacity:"
echo "  - 3 vendor-pricing instances"
echo "  - Each can scale 8-64 threads"
echo "  - Estimated max: ~300 RPS"
echo ""
echo "⚠️  This will test the LIMITS of our current system!"
echo ""

# Show current deployment
echo "Current deployment:"
docker compose -f infra/docker-compose.yml ps --format "table {{.Service}}\t{{.Name}}\t{{.Status}}" | grep -E "vendor-pricing|edge-gateway|Service"
echo ""

# Count instances
PRICING_COUNT=$(docker compose -f infra/docker-compose.yml ps vendor-pricing -q | wc -l | tr -d ' ')
GATEWAY_COUNT=$(docker compose -f infra/docker-compose.yml ps edge-gateway -q | wc -l | tr -d ' ')

echo "📊 Running with:"
echo "  vendor-pricing: $PRICING_COUNT instances"
echo "  edge-gateway: $GATEWAY_COUNT instances"
echo "  Estimated capacity: ~$((PRICING_COUNT * 100)) RPS"
echo ""

read -p "Press Enter to start flash sale simulation..."
echo ""

# Start background monitoring
echo "Starting background monitors..."
echo "  Logs: infra/flash-sale-logs.txt"
echo ""

# Monitor autoscaler in background
docker compose -f infra/docker-compose.yml logs -f vendor-pricing 2>&1 | grep -E "snapshot|SCALE" > infra/flash-sale-logs.txt &
MONITOR_PID=$!

# Run flash sale test
echo "🚀 Launching flash sale simulation..."
cd "$SCRIPT_DIR"

START_TIME=$(date +%s)

jmeter -n -t dealcart-flash-sale.jmx \
  -l flash-sale.jtl \
  -e -o results-flash-sale \
  -Juser.dir="$PROJECT_ROOT" \
  -Jjmeter.reportgenerator.overall_granularity=1000 2>&1 | grep summary

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Stop monitoring
kill $MONITOR_PID 2>/dev/null || true

echo ""
echo "✅ Flash sale simulation complete in ${DURATION}s!"
echo ""

# Extract metrics
echo "Extracting metrics..."
FLASH_AVG=$(awk -F',' 'NR>1 {sum+=$2; count++} END {if(count>0) print int(sum/count); else print 0}' flash-sale.jtl)
FLASH_P50=$(awk -F',' 'NR>1 {print $2}' flash-sale.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{if(c>0) print a[int(c*0.50)]; else print 0}')
FLASH_P95=$(awk -F',' 'NR>1 {print $2}' flash-sale.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{if(c>0) print a[int(c*0.95)]; else print 0}')
FLASH_P99=$(awk -F',' 'NR>1 {print $2}' flash-sale.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{if(c>0) print a[int(c*0.99)]; else print 0}')
FLASH_COUNT=$(awk -F',' 'NR>1 {count++} END {print count}' flash-sale.jtl)
FLASH_ERRORS=$(awk -F',' 'NR>1 && $8=="false" {errors++} END {print errors+0}' flash-sale.jtl)
FLASH_ERROR_RATE=$(echo "scale=2; $FLASH_ERRORS / $FLASH_COUNT * 100" | bc)
FLASH_TPS=$(echo "scale=1; $FLASH_COUNT / $DURATION" | bc)

echo ""
echo "========================================="
echo "FLASH SALE RESULTS"
echo "========================================="
echo ""
echo "Load Profile:"
echo "  Peak Concurrency: 500 users"
echo "  Total Requests:   $FLASH_COUNT"
echo "  Duration:         ${DURATION}s"
echo "  Throughput:       ${FLASH_TPS} TPS"
echo ""
echo "Latency Metrics:"
echo "  Average:  ${FLASH_AVG}ms"
echo "  P50:      ${FLASH_P50}ms"
echo "  P95:      ${FLASH_P95}ms ⭐"
echo "  P99:      ${FLASH_P99}ms"
echo ""
echo "Reliability:"
echo "  Errors:      ${FLASH_ERRORS}"
echo "  Error Rate:  ${FLASH_ERROR_RATE}%"
echo "  Success Rate: $(echo "scale=2; 100 - $FLASH_ERROR_RATE" | bc)%"
echo ""

# Show autoscaling behavior
echo "========================================="
echo "AUTOSCALING BEHAVIOR"
echo "========================================="
echo ""
echo "Thread Scaling (per instance):"
cat infra/flash-sale-logs.txt | grep "SCALE" | head -10 || echo "  No scaling events (load was within capacity)"
echo ""
echo "Periodic Snapshots (last 5):"
cat infra/flash-sale-logs.txt | grep "snapshot" | tail -5
echo ""

# Analysis
echo "========================================="
echo "ANALYSIS"
echo "========================================="
echo ""

if (( $(echo "$FLASH_TPS >= 200" | bc -l) )); then
    echo "✅ High Throughput: ${FLASH_TPS} TPS (good for flash sale)"
else
    echo "⚠️  Low Throughput: ${FLASH_TPS} TPS (may struggle with real flash sale)"
fi

if (( $(echo "$FLASH_P95 <= 2000" | bc -l) )); then
    echo "✅ Acceptable P95: ${FLASH_P95}ms (under extreme load)"
else
    echo "❌ High P95: ${FLASH_P95}ms (users will notice delays)"
fi

if (( $(echo "$FLASH_ERROR_RATE < 5.0" | bc -l) )); then
    echo "✅ Low Error Rate: ${FLASH_ERROR_RATE}% (good reliability)"
else
    echo "❌ High Error Rate: ${FLASH_ERROR_RATE}% (many failed requests)"
fi

echo ""
echo "========================================="
echo "RECOMMENDATIONS"
echo "========================================="
echo ""

if (( $(echo "$FLASH_TPS < 250" | bc -l) )); then
    echo "🔧 Current system struggles with flash sale traffic"
    echo "   Solution: AWS Auto Scaling Groups"
    echo "   - Automatically scale to 8-10 instances during spikes"
    echo "   - Handle 500+ RPS with sub-second response times"
    echo ""
fi

if (( $(echo "$FLASH_P95 > 1000" | bc -l) )); then
    echo "🔧 High latency under burst load"
    echo "   Solution: More instances + better load balancing"
    echo "   - Pre-warm instances before flash sale"
    echo "   - Use CDN for static content"
    echo ""
fi

echo "========================================="
echo "View HTML report:"
echo "  open $SCRIPT_DIR/results-flash-sale/index.html"
echo ""
echo "View autoscaling logs:"
echo "  cat $PROJECT_ROOT/infra/flash-sale-logs.txt"
echo ""
echo "Next: Implement AWS Auto Scaling for true flash sale handling"
echo "========================================="

