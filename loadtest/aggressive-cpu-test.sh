#!/bin/bash

# Aggressive CPU Load Test for MacBook Pro
# This will actually stress the system to trigger auto-scaling

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================="
echo "Aggressive CPU Load Test"
echo "MacBook Pro Stress Test for Auto-Scaling"
echo "========================================="
echo ""

cd "$PROJECT_ROOT"

# Function to get current metrics
get_metrics() {
    local container_name=$(docker compose -f infra/docker-compose.yml ps vendor-pricing -q | head -1)
    docker exec "$container_name" wget -qO- "http://localhost:10100/metrics" 2>/dev/null || echo '{"cpuUsage":0,"memoryUsage":0}'
}

# Function to get current instance count
get_instances() {
    docker compose -f infra/docker-compose.yml ps vendor-pricing -q | wc -l | tr -d ' '
}

echo "Starting aggressive CPU test..."
echo ""

# Show initial state
echo "Initial State:"
echo "Instances: $(get_instances)"
echo "CPU/Memory:"
get_metrics | grep -o '"cpuUsage":[0-9.]*\|"memoryUsage":[0-9.]*'
echo ""

# Start production auto-scaler in background
echo "Starting production auto-scaler..."
nohup ./infra/production-scaler.sh > infra/aggressive-test.log 2>&1 &
SCALER_PID=$!
echo "Auto-scaler PID: $SCALER_PID"
sleep 5

# Function to send massive concurrent load
send_aggressive_load() {
    local round=$1
    local concurrent=$2
    
    echo "Round $round: Sending $concurrent concurrent requests..."
    
    # Send requests in batches to avoid overwhelming the system
    for batch in {1..10}; do
        for i in $(seq 1 $((concurrent / 10))); do
            curl -s "http://localhost/api/quote?productId=sku-laptop&mode=best" > /dev/null &
        done
        sleep 0.1  # Small delay between batches
    done
    
    # Wait for all requests to complete
    wait
    echo "Round $round completed"
}

# Function to create CPU stress (additional load)
create_cpu_stress() {
    echo "Creating additional CPU stress..."
    
    # Start background processes that consume CPU
    for i in {1..20}; do
        (
            while true; do
                # CPU-intensive operations
                python3 -c "
import time
import math
start = time.time()
while time.time() - start < 10:
    math.sqrt(i * 1000000)
" 2>/dev/null || true
            done
        ) &
    done
    
    echo "CPU stress processes started"
}

# Main aggressive test
echo "========================================="
echo "AGGRESSIVE LOAD TESTING"
echo "========================================="
echo ""

# Phase 1: Moderate load
echo "Phase 1: Moderate load (500 concurrent)"
send_aggressive_load 1 500
sleep 5
echo "CPU after Phase 1:"
get_metrics | grep -o '"cpuUsage":[0-9.]*\|"memoryUsage":[0-9.]*'
echo ""

# Phase 2: High load
echo "Phase 2: High load (1000 concurrent)"
send_aggressive_load 2 1000
sleep 5
echo "CPU after Phase 2:"
get_metrics | grep -o '"cpuUsage":[0-9.]*\|"memoryUsage":[0-9.]*'
echo ""

# Phase 3: Extreme load + CPU stress
echo "Phase 3: Extreme load (2000 concurrent) + CPU stress"
create_cpu_stress
send_aggressive_load 3 2000
sleep 10
echo "CPU after Phase 3:"
get_metrics | grep -o '"cpuUsage":[0-9.]*\|"memoryUsage":[0-9.]*'
echo ""

# Phase 4: Sustained extreme load
echo "Phase 4: Sustained extreme load (3000 concurrent)"
for round in {4..8}; do
    send_aggressive_load $round 3000
    sleep 2
    echo "CPU after Round $round:"
    get_metrics | grep -o '"cpuUsage":[0-9.]*\|"memoryUsage":[0-9.]*'
    echo ""
done

# Check final state
echo "========================================="
echo "FINAL RESULTS"
echo "========================================="
echo ""

echo "Final Instances: $(get_instances)"
echo "Final CPU/Memory:"
get_metrics | grep -o '"cpuUsage":[0-9.]*\|"memoryUsage":[0-9.]*'
echo ""

echo "Auto-scaler logs (last 10 lines):"
tail -10 infra/aggressive-test.log
echo ""

# Clean up
echo "Cleaning up..."
kill $SCALER_PID 2>/dev/null || true
pkill -f "python3 -c" 2>/dev/null || true

echo "========================================="
echo "AGGRESSIVE TEST COMPLETE"
echo "========================================="
