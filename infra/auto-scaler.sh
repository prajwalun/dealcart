#!/bin/bash

# DealCart+ Traffic-Based Auto-Scaler
# Monitors RPS, latency, and error rate to automatically scale instances

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
METRICS_URL="http://localhost:10100/metrics"
HEALTH_CHECK_URL="http://localhost:10100/health"

# Scaling thresholds
RPS_THRESHOLD_UP=150
RPS_THRESHOLD_DOWN=50
P95_THRESHOLD_UP=500
P95_THRESHOLD_DOWN=200
ERROR_RATE_THRESHOLD=2.0

# Scaling limits
MIN_INSTANCES=1
MAX_INSTANCES=10
SCALE_STEP=2

# Cooldown periods (seconds)
SCALE_UP_COOLDOWN=60
SCALE_DOWN_COOLDOWN=300

# State tracking
LAST_SCALE_UP=0
LAST_SCALE_DOWN=0
CURRENT_INSTANCES=3

echo "========================================="
echo "DealCart+ Traffic-Based Auto-Scaler"
echo "========================================="
echo ""
echo "Configuration:"
echo "  RPS Threshold (Up):   $RPS_THRESHOLD_UP"
echo "  RPS Threshold (Down): $RPS_THRESHOLD_DOWN"
echo "  P95 Threshold (Up):   ${P95_THRESHOLD_UP}ms"
echo "  P95 Threshold (Down): ${P95_THRESHOLD_DOWN}ms"
echo "  Error Rate Threshold: ${ERROR_RATE_THRESHOLD}%"
echo "  Min Instances:        $MIN_INSTANCES"
echo "  Max Instances:        $MAX_INSTANCES"
echo "  Scale Step:           $SCALE_STEP"
echo ""

cd "$PROJECT_ROOT"

# Function to get current instance count
get_current_instances() {
    docker compose -f "$COMPOSE_FILE" ps vendor-pricing -q | wc -l | tr -d ' '
}

# Function to get traffic metrics
get_traffic_metrics() {
    local response
    response=$(curl -s "$METRICS_URL" 2>/dev/null || echo '{"rps":0,"errorRate":0,"p95Latency":0}')
    
    # Extract metrics using jq or basic parsing
    if command -v jq &> /dev/null; then
        echo "$response" | jq -r '.rps, .errorRate, .p95Latency'
    else
        # Basic parsing without jq
        echo "$response" | grep -o '"rps":[0-9.]*' | cut -d: -f2
        echo "$response" | grep -o '"errorRate":[0-9.]*' | cut -d: -f2
        echo "$response" | grep -o '"p95Latency":[0-9]*' | cut -d: -f2
    fi
}

# Function to check if scaling is allowed
can_scale_up() {
    local current_time=$(date +%s)
    local time_since_last_scale=$((current_time - LAST_SCALE_UP))
    [ $time_since_last_scale -ge $SCALE_UP_COOLDOWN ] && [ $CURRENT_INSTANCES -lt $MAX_INSTANCES ]
}

can_scale_down() {
    local current_time=$(date +%s)
    local time_since_last_scale=$((current_time - LAST_SCALE_DOWN))
    [ $time_since_last_scale -ge $SCALE_DOWN_COOLDOWN ] && [ $CURRENT_INSTANCES -gt $MIN_INSTANCES ]
}

# Function to scale instances
scale_instances() {
    local target_instances=$1
    local current_instances=$2
    
    if [ $target_instances -eq $current_instances ]; then
        return 0
    fi
    
    echo "🔄 Scaling vendor-pricing: $current_instances → $target_instances instances"
    
    # Scale the service
    docker compose -f "$COMPOSE_FILE" up -d --scale vendor-pricing=$target_instances --no-recreate
    
    # Wait for new instances to be healthy
    echo "⏳ Waiting for instances to be healthy..."
    sleep 30
    
    # Verify scaling
    local new_count=$(get_current_instances)
    if [ $new_count -eq $target_instances ]; then
        echo "✅ Successfully scaled to $target_instances instances"
        CURRENT_INSTANCES=$target_instances
        return 0
    else
        echo "❌ Scaling failed. Expected $target_instances, got $new_count"
        return 1
    fi
}

# Function to log metrics
log_metrics() {
    local rps=$1
    local error_rate=$2
    local p95_latency=$3
    local instances=$4
    local action=$5
    
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] RPS=$rps, ErrorRate=${error_rate}%, P95=${p95_latency}ms, Instances=$instances, Action=$action"
}

# Main scaling loop
run_auto_scaler() {
    echo "🚀 Starting auto-scaler monitoring loop..."
    echo "Press Ctrl+C to stop"
    echo ""
    
    while true; do
        # Get current metrics
        local metrics
        metrics=$(get_traffic_metrics)
        
        if [ -z "$metrics" ] || [ "$metrics" = "0 0 0" ]; then
            echo "⚠️  No metrics available, skipping check..."
            sleep 10
            continue
        fi
        
        # Parse metrics
        local rps=$(echo "$metrics" | head -1)
        local error_rate=$(echo "$metrics" | head -2 | tail -1)
        local p95_latency=$(echo "$metrics" | head -3 | tail -1)
        
        # Get current instance count
        local current_instances=$(get_current_instances)
        CURRENT_INSTANCES=$current_instances
        
        # Determine scaling action
        local action="none"
        local target_instances=$current_instances
        
        # Check scale-up conditions
        if can_scale_up && ([ $(echo "$rps > $RPS_THRESHOLD_UP" | bc -l) -eq 1 ] || 
                           [ $(echo "$p95_latency > $P95_THRESHOLD_UP" | bc -l) -eq 1 ] || 
                           [ $(echo "$error_rate > $ERROR_RATE_THRESHOLD" | bc -l) -eq 1 ]); then
            
            target_instances=$((current_instances + SCALE_STEP))
            if [ $target_instances -gt $MAX_INSTANCES ]; then
                target_instances=$MAX_INSTANCES
            fi
            action="scale-up"
            LAST_SCALE_UP=$(date +%s)
            
        # Check scale-down conditions
        elif can_scale_down && [ $(echo "$rps < $RPS_THRESHOLD_DOWN" | bc -l) -eq 1 ] && 
             [ $(echo "$p95_latency < $P95_THRESHOLD_DOWN" | bc -l) -eq 1 ]; then
            
            target_instances=$((current_instances - SCALE_STEP))
            if [ $target_instances -lt $MIN_INSTANCES ]; then
                target_instances=$MIN_INSTANCES
            fi
            action="scale-down"
            LAST_SCALE_DOWN=$(date +%s)
        fi
        
        # Log current state
        log_metrics "$rps" "$error_rate" "$p95_latency" "$current_instances" "$action"
        
        # Perform scaling if needed
        if [ "$action" != "none" ]; then
            if scale_instances $target_instances $current_instances; then
                echo "✅ Auto-scaling completed: $action"
            else
                echo "❌ Auto-scaling failed: $action"
            fi
        fi
        
        # Wait before next check
        sleep 30
    done
}

# Check if bc is available
if ! command -v bc &> /dev/null; then
    echo "❌ 'bc' command not found. Install with: brew install bc"
    exit 1
fi

# Check if services are running
if ! docker compose -f "$COMPOSE_FILE" ps | grep -q "vendor-pricing.*Up"; then
    echo "❌ Services not running! Start with: docker compose -f $COMPOSE_FILE up -d"
    exit 1
fi

# Check if metrics endpoint is available
if ! curl -s "$HEALTH_CHECK_URL" > /dev/null; then
    echo "❌ Metrics endpoint not available at $HEALTH_CHECK_URL"
    echo "   Make sure vendor-pricing service is running with metrics enabled"
    exit 1
fi

echo "✅ All checks passed. Starting auto-scaler..."
echo ""

# Run the auto-scaler
run_auto_scaler
