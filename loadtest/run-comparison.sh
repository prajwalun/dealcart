#!/bin/bash

# DealCart+ Load Test: Baseline vs Autoscaled Comparison
# This script runs both tests automatically and generates comparison reports

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================="
echo "DealCart+ Load Test Comparison"
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

# Clean old results
echo "Cleaning old results..."
rm -rf "$SCRIPT_DIR/results-baseline" "$SCRIPT_DIR/results-autoscaled"
rm -f "$SCRIPT_DIR/baseline.jtl" "$SCRIPT_DIR/auto.jtl"
echo ""

#================================================
# TEST 1: BASELINE (Fixed 8 threads)
#================================================
echo "========================================="
echo "TEST 1: BASELINE (Fixed pool, no scaling)"
echo "========================================="
echo ""
echo "Setting ADAPTIVE_MAX=8 (locking pool size)..."

# Update docker-compose.yml for baseline
cd "$PROJECT_ROOT"
sed -i.bak 's/ADAPTIVE_MAX=64/ADAPTIVE_MAX=8/' infra/docker-compose.yml

# Restart vendor-pricing
echo "Restarting vendor-pricing..."
docker compose -f infra/docker-compose.yml up -d vendor-pricing

# Wait for healthy
echo "Waiting for service to be healthy (15 seconds)..."
sleep 15

# Verify configuration
echo "Verifying configuration..."
docker compose -f infra/docker-compose.yml logs vendor-pricing | grep "Autoscaling config" | tail -1

echo ""
echo "🚀 Starting BASELINE test (10 minutes at ~200 RPS)..."
echo "   Watch progress: tail -f jmeter.log (in another terminal)"
echo ""

cd "$SCRIPT_DIR"
jmeter -n -t dealcart.jmx \
  -l baseline.jtl \
  -e -o results-baseline \
  -Juser.dir="$PROJECT_ROOT" \
  -Jjmeter.reportgenerator.overall_granularity=1000

echo ""
echo "✅ BASELINE test complete!"
echo "   Results: $SCRIPT_DIR/results-baseline/index.html"
echo ""

#================================================
# TEST 2: AUTOSCALED (8-64 threads)
#================================================
echo "========================================="
echo "TEST 2: AUTOSCALED (Adaptive pool 8-64)"
echo "========================================="
echo ""
echo "Restoring ADAPTIVE_MAX=64 (enabling autoscaling)..."

# Restore docker-compose.yml for autoscaling
cd "$PROJECT_ROOT"
mv infra/docker-compose.yml.bak infra/docker-compose.yml

# Restart vendor-pricing
echo "Restarting vendor-pricing..."
docker compose -f infra/docker-compose.yml up -d vendor-pricing

# Wait for healthy
echo "Waiting for service to be healthy (15 seconds)..."
sleep 15

# Verify configuration
echo "Verifying configuration..."
docker compose -f infra/docker-compose.yml logs vendor-pricing | grep "Autoscaling config" | tail -1

echo ""
echo "🚀 Starting AUTOSCALED test (10 minutes at ~200 RPS)..."
echo "   Watch scaling: docker compose logs -f vendor-pricing | grep -E 'snapshot|SCALE'"
echo ""

cd "$SCRIPT_DIR"
jmeter -n -t dealcart.jmx \
  -l auto.jtl \
  -e -o results-autoscaled \
  -Juser.dir="$PROJECT_ROOT" \
  -Jjmeter.reportgenerator.overall_granularity=1000

echo ""
echo "✅ AUTOSCALED test complete!"
echo "   Results: $SCRIPT_DIR/results-autoscaled/index.html"
echo ""

#================================================
# COMPARISON SUMMARY
#================================================
echo "========================================="
echo "COMPARISON SUMMARY"
echo "========================================="
echo ""

# Extract key metrics from JTL files
echo "Extracting metrics..."

# Baseline metrics - use sort instead of awk asort (BSD awk compatible)
BASELINE_AVG=$(awk -F',' 'NR>1 {sum+=$2; count++} END {print int(sum/count)}' baseline.jtl)
BASELINE_P95=$(awk -F',' 'NR>1 {print $2}' baseline.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.95)]}')
BASELINE_P99=$(awk -F',' 'NR>1 {print $2}' baseline.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.99)]}')
BASELINE_COUNT=$(awk -F',' 'NR>1 {count++} END {print count}' baseline.jtl)
BASELINE_ERRORS=$(awk -F',' 'NR>1 && $8=="false" {errors++} END {print errors+0}' baseline.jtl)

# Autoscaled metrics
AUTO_AVG=$(awk -F',' 'NR>1 {sum+=$2; count++} END {print int(sum/count)}' auto.jtl)
AUTO_P95=$(awk -F',' 'NR>1 {print $2}' auto.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.95)]}')
AUTO_P99=$(awk -F',' 'NR>1 {print $2}' auto.jtl | sort -n | awk 'BEGIN{c=0} {a[c]=$1; c++} END{print a[int(c*0.99)]}')
AUTO_COUNT=$(awk -F',' 'NR>1 {count++} END {print count}' auto.jtl)
AUTO_ERRORS=$(awk -F',' 'NR>1 && $8=="false" {errors++} END {print errors+0}' auto.jtl)

# Calculate improvements
P95_IMPROVEMENT=$(echo "scale=1; ($BASELINE_P95 - $AUTO_P95) / $BASELINE_P95 * 100" | bc)
P99_IMPROVEMENT=$(echo "scale=1; ($BASELINE_P99 - $AUTO_P99) / $BASELINE_P99 * 100" | bc)

echo "Metric              | Baseline      | Autoscaled    | Improvement"
echo "--------------------|---------------|---------------|-------------"
echo "Average Latency     | ${BASELINE_AVG}ms       | ${AUTO_AVG}ms       | -"
echo "P95 Latency         | ${BASELINE_P95}ms       | ${AUTO_P95}ms       | ${P95_IMPROVEMENT}%"
echo "P99 Latency         | ${BASELINE_P99}ms       | ${AUTO_P99}ms       | ${P99_IMPROVEMENT}%"
echo "Total Requests      | $BASELINE_COUNT       | $AUTO_COUNT       | -"
echo "Errors              | $BASELINE_ERRORS         | $AUTO_ERRORS         | -"
echo ""

# Check acceptance criteria
echo "Acceptance Criteria:"
if (( $(echo "$AUTO_P95 <= 250" | bc -l) )); then
    echo "✅ P95 ≤ 250ms: PASS ($AUTO_P95 ms)"
else
    echo "❌ P95 ≤ 250ms: FAIL ($AUTO_P95 ms)"
fi

if (( $(echo "$AUTO_P99 <= 500" | bc -l) )); then
    echo "✅ P99 ≤ 500ms: PASS ($AUTO_P99 ms)"
else
    echo "❌ P99 ≤ 500ms: FAIL ($AUTO_P99 ms)"
fi

if (( $(echo "$AUTO_P95 < $BASELINE_P95" | bc -l) )); then
    echo "✅ Autoscaled P95 < Baseline P95: PASS"
else
    echo "❌ Autoscaled P95 < Baseline P95: FAIL"
fi

echo ""
echo "========================================="
echo "View detailed reports:"
echo "  Baseline:    open $SCRIPT_DIR/results-baseline/index.html"
echo "  Autoscaled:  open $SCRIPT_DIR/results-autoscaled/index.html"
echo ""
echo "Check autoscaler logs:"
echo "  docker compose -f $PROJECT_ROOT/infra/docker-compose.yml logs vendor-pricing | grep -E 'snapshot|SCALE'"
echo "========================================="

