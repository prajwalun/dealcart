#!/bin/bash

# DealCart+ Horizontal Scaling Demo
# Shows how the system scales instances up and down based on load

set -e

COMPOSE_FILE="docker-compose.yml"

echo "========================================="
echo "DealCart+ Horizontal Scaling Demo"
echo "========================================="
echo ""

# Function to show current scaling status
show_status() {
    echo "Current Deployment Status:"
    echo ""
    docker compose -f $COMPOSE_FILE ps --format "table {{.Service}}\t{{.Name}}\t{{.Status}}" | grep -E "vendor-pricing|edge-gateway|Service"
    echo ""
    
    # Count instances
    PRICING_COUNT=$(docker compose -f $COMPOSE_FILE ps vendor-pricing --format json | jq -s 'length')
    GATEWAY_COUNT=$(docker compose -f $COMPOSE_FILE ps edge-gateway --format json | jq -s 'length')
    
    echo "📊 Instance Counts:"
    echo "  vendor-pricing: $PRICING_COUNT instances"
    echo "  edge-gateway: $GATEWAY_COUNT instances"
    echo ""
}

# Function to scale services
scale_services() {
    local pricing_replicas=$1
    local gateway_replicas=$2
    
    echo "Scaling to: vendor-pricing=$pricing_replicas, edge-gateway=$gateway_replicas..."
    docker compose -f $COMPOSE_FILE up -d --scale vendor-pricing=$pricing_replicas --scale edge-gateway=$gateway_replicas --no-recreate
    
    echo "Waiting for services to be healthy..."
    sleep 15
    
    show_status
}

# Main demo workflow
case "${1:-demo}" in
    "demo")
        echo "🎬 Running full horizontal scaling demo..."
        echo ""
        
        echo "Step 1: Start with minimal instances (1 each)"
        scale_services 1 1
        echo "Press Enter to continue to high load..."
        read
        
        echo ""
        echo "Step 2: Scale UP for high traffic (3 pricing, 2 gateway)"
        scale_services 3 2
        echo "Press Enter to continue to peak load..."
        read
        
        echo ""
        echo "Step 3: Peak traffic (5 pricing, 3 gateway)"
        scale_services 5 3
        echo "Press Enter to scale down..."
        read
        
        echo ""
        echo "Step 4: Scale DOWN as traffic decreases (2 pricing, 1 gateway)"
        scale_services 2 1
        echo ""
        
        echo "✅ Demo complete! System scaled from 1→5→2 instances."
        ;;
        
    "up")
        REPLICAS=${2:-3}
        echo "Scaling UP to $REPLICAS instances..."
        scale_services $REPLICAS 2
        ;;
        
    "down")
        echo "Scaling DOWN to 1 instance each..."
        scale_services 1 1
        ;;
        
    "status")
        show_status
        ;;
        
    "test")
        echo "Running quick load test to verify load balancing..."
        for i in {1..10}; do
            curl -s "http://localhost/api/quote?productId=sku-laptop&mode=best" > /dev/null
            echo "Request $i sent"
            sleep 0.1
        done
        echo ""
        echo "Check which instances handled requests:"
        docker compose -f $COMPOSE_FILE logs edge-gateway | grep "GET /api/quote" | tail -10
        ;;
        
    *)
        echo "Usage: $0 {demo|up|down|status|test}"
        echo ""
        echo "  demo    - Run interactive scaling demo (1→3→5→2 instances)"
        echo "  up [N]  - Scale up to N pricing instances (default: 3)"
        echo "  down    - Scale down to 1 instance each"
        echo "  status  - Show current instance counts"
        echo "  test    - Send test requests and show load distribution"
        echo ""
        exit 1
        ;;
esac

