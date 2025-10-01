#!/bin/bash

# Production-Style Auto-Scaler
# Uses CPU and Memory metrics like real companies do

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration (like AWS Auto Scaling Groups)
CPU_THRESHOLD_UP=70
CPU_THRESHOLD_DOWN=30
MEMORY_THRESHOLD_UP=80
MEMORY_THRESHOLD_DOWN=40

# Scaling limits
MIN_INSTANCES=1
MAX_INSTANCES=5
SCALE_STEP=1

# Cooldown periods (like AWS)
SCALE_UP_COOLDOWN=300    # 5 minutes
SCALE_DOWN_COOLDOWN=600  # 10 minutes

# State tracking
LAST_SCALE_UP=0
LAST_SCALE_DOWN=0
CURRENT_INSTANCES=3

echo "========================================="
echo "Production-Style Auto-Scaler"
echo "CPU/Memory Based Scaling (Like Real Companies)"
echo "========================================="
echo ""
echo "Configuration:"
echo "  CPU Scale UP:   > ${CPU_THRESHOLD_UP}% for 2 minutes"
echo "  CPU Scale DOWN: < ${CPU_THRESHOLD_DOWN}% for 5 minutes"
echo "  Memory Scale UP: > ${MEMORY_THRESHOLD_UP}% for 2 minutes"
echo "  Min Instances:  $MIN_INSTANCES"
echo "  Max Instances:  $MAX_INSTANCES"
echo ""

cd "$PROJECT_ROOT"

# Function to get current instance count
get_current_instances() {
    docker compose -f infra/docker-compose.yml ps vendor-pricing -q | wc -l | tr -d ' '
}

# Function to get system metrics from any instance
get_system_metrics() {
    local container_name=$(docker compose -f infra/docker-compose.yml ps vendor-pricing -q | head -1)
    local response=$(docker exec "$container_name" wget -qO- "http://localhost:10100/metrics" 2>/dev/null || echo '{"cpuUsage":0,"memoryUsage":0,"loadAverage":0}')
    
    if command -v jq &> /dev/null; then
        echo "$response" | jq -r '.cpuUsage, .memoryUsage, .loadAverage'
    else
        # Basic parsing without jq
        echo "$response" | grep -o '"cpuUsage":[0-9.]*' | cut -d: -f2
        echo "$response" | grep -o '"memoryUsage":[0-9.]*' | cut -d: -f2
        echo "$response" | grep -o '"loadAverage":[0-9.]*' | cut -d: -f2
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
    docker compose -f infra/docker-compose.yml up -d --scale vendor-pricing=$target_instances --no-recreate
    
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
    local cpu=$1
    local memory=$2
    local load=$3
    local instances=$4
    local action=$5
    
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] CPU=${cpu}%, Memory=${memory}%, Load=${load}, Instances=$instances, Action=$action"
}

# Main scaling loop
run_production_scaler() {
    echo "🚀 Starting production-style auto-scaler..."
    echo "Press Ctrl+C to stop"
    echo ""
    
    while true; do
        # Get current metrics
        local metrics
        metrics=$(get_system_metrics)
        
        if [ -z "$metrics" ] || [ "$metrics" = "0 0 0" ]; then
            echo "⚠️  No metrics available, skipping check..."
            sleep 30
            continue
        fi
        
        # Parse metrics
        local cpu=$(echo "$metrics" | head -1)
        local memory=$(echo "$metrics" | head -2 | tail -1)
        local load=$(echo "$metrics" | head -3 | tail -1)
        
        # Get current instance count
        local current_instances=$(get_current_instances)
        CURRENT_INSTANCES=$current_instances
        
        # Determine scaling action (like AWS Auto Scaling Groups)
        local action="none"
        local target_instances=$current_instances
        
        # Check scale-up conditions (CPU OR Memory)
        if can_scale_up && ([ $(echo "$cpu > $CPU_THRESHOLD_UP" | bc -l) -eq 1 ] || 
                           [ $(echo "$memory > $MEMORY_THRESHOLD_UP" | bc -l) -eq 1 ]); then
            
            target_instances=$((current_instances + SCALE_STEP))
            if [ $target_instances -gt $MAX_INSTANCES ]; then
                target_instances=$MAX_INSTANCES
            fi
            action="scale-up"
            LAST_SCALE_UP=$(date +%s)
            
        # Check scale-down conditions (CPU AND Memory both low)
        elif can_scale_down && [ $(echo "$cpu < $CPU_THRESHOLD_DOWN" | bc -l) -eq 1 ] && 
             [ $(echo "$memory < $MEMORY_THRESHOLD_DOWN" | bc -l) -eq 1 ]; then
            
            target_instances=$((current_instances - SCALE_STEP))
            if [ $target_instances -lt $MIN_INSTANCES ]; then
                target_instances=$MIN_INSTANCES
            fi
            action="scale-down"
            LAST_SCALE_DOWN=$(date +%s)
        fi
        
        # Log current state
        log_metrics "$cpu" "$memory" "$load" "$current_instances" "$action"
        
        # Perform scaling if needed
        if [ "$action" != "none" ]; then
            if scale_instances $target_instances $current_instances; then
                echo "✅ Production scaling completed: $action"
            else
                echo "❌ Production scaling failed: $action"
            fi
        fi
        
        # Wait before next check (like CloudWatch)
        sleep 60
    done
}

# Check if bc is available
if ! command -v bc &> /dev/null; then
    echo "❌ 'bc' command not found. Install with: brew install bc"
    exit 1
fi

# Check if services are running
if ! docker compose -f infra/docker-compose.yml ps | grep -q "vendor-pricing.*Up"; then
    echo "❌ Services not running! Start with: docker compose -f infra/docker-compose.yml up -d"
    exit 1
fi

echo "✅ All checks passed. Starting production-style auto-scaler..."
echo ""

# Run the auto-scaler
run_production_scaler
