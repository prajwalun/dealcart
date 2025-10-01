#!/bin/bash

# DealCart+ High-Volume Scale Tests (50K and 100K requests)
# Tests autoscaling behavior under sustained high load

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================="
echo "DealCart+ High-Volume Scale Tests"
echo "========================================="
echo ""

# Check JMeter is installed
if ! command -v jmeter &> /dev/null; then
    echo "❌ JMeter not found!"
    echo "Install with: brew install jmeter"
    exit 1
fi

echo "✅ JMeter found: $(jmeter -v | head -1)"
echo ""

# Check services are running
echo "Checking Docker services..."
cd "$PROJECT_ROOT"
if ! docker compose -f infra/docker-compose.yml ps | grep -q "vendor-pricing.*Up"; then
    echo "❌ vendor-pricing service not running!"
    echo "Start with: docker compose -f infra/docker-compose.yml up -d"
    exit 1
fi

echo "✅ Services running"
echo ""

# Ensure autoscaling is enabled
echo "Verifying autoscaling is enabled..."
docker compose -f infra/docker-compose.yml logs vendor-pricing | grep "Autoscaling config" | tail -1
echo ""

# Clean old results
echo "Cleaning old scale test results..."
rm -rf "$SCRIPT_DIR/results-50k" "$SCRIPT_DIR/results-100k"
rm -f "$SCRIPT_DIR/scale-50k.jtl" "$SCRIPT_DIR/scale-100k.jtl"
echo ""

#================================================
# TEST 1: 50K REQUESTS
#================================================
echo "========================================="
echo "TEST 1: 50K REQUESTS (50 threads × 1000 loops)"
echo "========================================="
echo ""
echo "🚀 Starting 50K request test..."
echo "   Expected duration: ~5-7 minutes"
echo "   Watch scaling: docker compose logs -f vendor-pricing | grep -E 'snapshot|SCALE'"
echo ""

cd "$SCRIPT_DIR"
START_50K=$(date +%s)

jmeter -n -t dealcart-50k.jmx \
  -l scale-50k.jtl \
  -e -o results-50k \
  -Juser.dir="$PROJECT_ROOT" \
  -Jjmeter.reportgenerator.overall_granularity=5000

END_50K=$(date +%s)
DURATION_50K=$((END_50K - START_50K))

echo ""
echo "✅ 50K test complete in ${DURATION_50K}s!"
echo "   Results: $SCRIPT_DIR/results-50k/index.html"
echo ""

# Extract 50K metrics
echo "Extracting 50K metrics..."
SCALE_50K_AVG=$(awk -F',' 'NR>1 {sum+=$2; count++} END {print int(sum/count)}' scale-50k.jtl)
SCALE_50K_P50=$(awk -F',' 'NR>1 {print $2}' scale-50k.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.50)]}')
SCALE_50K_P95=$(awk -F',' 'NR>1 {print $2}' scale-50k.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.95)]}')
SCALE_50K_P99=$(awk -F',' 'NR>1 {print $2}' scale-50k.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.99)]}')
SCALE_50K_COUNT=$(awk -F',' 'NR>1 {count++} END {print count}' scale-50k.jtl)
SCALE_50K_ERRORS=$(awk -F',' 'NR>1 && $8=="false" {errors++} END {print errors+0}' scale-50k.jtl)
SCALE_50K_ERROR_RATE=$(echo "scale=2; $SCALE_50K_ERRORS / $SCALE_50K_COUNT * 100" | bc)
SCALE_50K_TPS=$(echo "scale=1; $SCALE_50K_COUNT / $DURATION_50K" | bc)

echo "50K Test Results:"
echo "  Total Requests:  $SCALE_50K_COUNT"
echo "  Duration:        ${DURATION_50K}s"
echo "  Throughput:      ${SCALE_50K_TPS} TPS"
echo "  Average Latency: ${SCALE_50K_AVG}ms"
echo "  P50 Latency:     ${SCALE_50K_P50}ms"
echo "  P95 Latency:     ${SCALE_50K_P95}ms"
echo "  P99 Latency:     ${SCALE_50K_P99}ms"
echo "  Errors:          ${SCALE_50K_ERRORS} (${SCALE_50K_ERROR_RATE}%)"
echo ""

# Wait a bit for system to stabilize
echo "Waiting 30 seconds for system to stabilize..."
sleep 30
echo ""

#================================================
# TEST 2: 100K REQUESTS
#================================================
echo "========================================="
echo "TEST 2: 100K REQUESTS (50 threads × 2000 loops)"
echo "========================================="
echo ""
echo "🚀 Starting 100K request test..."
echo "   Expected duration: ~10-15 minutes"
echo "   Watch scaling: docker compose logs -f vendor-pricing | grep -E 'snapshot|SCALE'"
echo ""

cd "$SCRIPT_DIR"
START_100K=$(date +%s)

jmeter -n -t dealcart-100k.jmx \
  -l scale-100k.jtl \
  -e -o results-100k \
  -Juser.dir="$PROJECT_ROOT" \
  -Jjmeter.reportgenerator.overall_granularity=10000

END_100K=$(date +%s)
DURATION_100K=$((END_100K - START_100K))

echo ""
echo "✅ 100K test complete in ${DURATION_100K}s!"
echo "   Results: $SCRIPT_DIR/results-100k/index.html"
echo ""

# Extract 100K metrics
echo "Extracting 100K metrics..."
SCALE_100K_AVG=$(awk -F',' 'NR>1 {sum+=$2; count++} END {print int(sum/count)}' scale-100k.jtl)
SCALE_100K_P50=$(awk -F',' 'NR>1 {print $2}' scale-100k.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.50)]}')
SCALE_100K_P95=$(awk -F',' 'NR>1 {print $2}' scale-100k.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.95)]}')
SCALE_100K_P99=$(awk -F',' 'NR>1 {print $2}' scale-100k.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.99)]}')
SCALE_100K_COUNT=$(awk -F',' 'NR>1 {count++} END {print count}' scale-100k.jtl)
SCALE_100K_ERRORS=$(awk -F',' 'NR>1 && $8=="false" {errors++} END {print errors+0}' scale-100k.jtl)
SCALE_100K_ERROR_RATE=$(echo "scale=2; $SCALE_100K_ERRORS / $SCALE_100K_COUNT * 100" | bc)
SCALE_100K_TPS=$(echo "scale=1; $SCALE_100K_COUNT / $DURATION_100K" | bc)

echo "100K Test Results:"
echo "  Total Requests:  $SCALE_100K_COUNT"
echo "  Duration:        ${DURATION_100K}s"
echo "  Throughput:      ${SCALE_100K_TPS} TPS"
echo "  Average Latency: ${SCALE_100K_AVG}ms"
echo "  P50 Latency:     ${SCALE_100K_P50}ms"
echo "  P95 Latency:     ${SCALE_100K_P95}ms"
echo "  P99 Latency:     ${SCALE_100K_P99}ms"
echo "  Errors:          ${SCALE_100K_ERRORS} (${SCALE_100K_ERROR_RATE}%)"
echo ""

#================================================
# SCALE TEST SUMMARY
#================================================
echo "========================================="
echo "SCALE TEST SUMMARY"
echo "========================================="
echo ""

echo "Metric              | 50K Test      | 100K Test     | Trend"
echo "--------------------|---------------|---------------|-------"
echo "Total Requests      | $SCALE_50K_COUNT        | $SCALE_100K_COUNT       | -"
echo "Duration            | ${DURATION_50K}s        | ${DURATION_100K}s      | -"
echo "Throughput          | ${SCALE_50K_TPS} TPS   | ${SCALE_100K_TPS} TPS  | -"
echo "Average Latency     | ${SCALE_50K_AVG}ms      | ${SCALE_100K_AVG}ms     | -"
echo "P50 Latency         | ${SCALE_50K_P50}ms      | ${SCALE_100K_P50}ms     | -"
echo "P95 Latency         | ${SCALE_50K_P95}ms      | ${SCALE_100K_P95}ms     | -"
echo "P99 Latency         | ${SCALE_50K_P99}ms      | ${SCALE_100K_P99}ms     | -"
echo "Error Rate          | ${SCALE_50K_ERROR_RATE}%       | ${SCALE_100K_ERROR_RATE}%      | -"
echo ""

# Check acceptance criteria
echo "Acceptance Criteria:"

if (( $(echo "$SCALE_50K_P95 <= 500" | bc -l) )); then
    echo "✅ 50K P95 ≤ 500ms: PASS ($SCALE_50K_P95 ms)"
else
    echo "❌ 50K P95 ≤ 500ms: FAIL ($SCALE_50K_P95 ms)"
fi

if (( $(echo "$SCALE_100K_P95 <= 500" | bc -l) )); then
    echo "✅ 100K P95 ≤ 500ms: PASS ($SCALE_100K_P95 ms)"
else
    echo "❌ 100K P95 ≤ 500ms: FAIL ($SCALE_100K_P95 ms)"
fi

if (( $(echo "$SCALE_50K_ERROR_RATE < 1.0" | bc -l) )); then
    echo "✅ 50K Error Rate < 1%: PASS (${SCALE_50K_ERROR_RATE}%)"
else
    echo "❌ 50K Error Rate < 1%: FAIL (${SCALE_50K_ERROR_RATE}%)"
fi

if (( $(echo "$SCALE_100K_ERROR_RATE < 1.0" | bc -l) )); then
    echo "✅ 100K Error Rate < 1%: PASS (${SCALE_100K_ERROR_RATE}%)"
else
    echo "❌ 100K Error Rate < 1%: FAIL (${SCALE_100K_ERROR_RATE}%)"
fi

echo ""
echo "========================================="
echo "View detailed reports:"
echo "  50K Test:   open $SCRIPT_DIR/results-50k/index.html"
echo "  100K Test:  open $SCRIPT_DIR/results-100k/index.html"
echo ""
echo "Check autoscaler behavior:"
echo "  docker compose -f $PROJECT_ROOT/infra/docker-compose.yml logs vendor-pricing | grep -E 'snapshot|SCALE' | tail -40"
echo "========================================="

