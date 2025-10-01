#!/bin/bash
set -e

echo "🚀 Setting up DealCart+ for local development..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi

echo "✅ Docker is running"

# Build Maven projects
echo "📦 Building Java services..."
./mvnw clean package -DskipTests

echo "✅ Java services built"

# Create web-ui env file
echo "📝 Creating web-ui environment file..."
cat > web-ui/.env.local <<EOF
# API Base URL (empty for same-origin via Caddy)
NEXT_PUBLIC_API_BASE_URL=
EOF

echo "✅ Environment file created"

# Start Docker Compose
echo "🐳 Starting Docker Compose stack..."
docker compose -f infra/docker-compose.yml up -d

echo ""
echo "⏳ Waiting for services to become healthy..."
sleep 5

# Check service status
docker compose -f infra/docker-compose.yml ps

echo ""
echo "✨ DealCart+ is ready!"
echo ""
echo "🌐 Access the app:"
echo "   - Full app: http://localhost"
echo "   - UI only: http://localhost:3000 (if running web-ui separately)"
echo "   - API only: http://localhost:8080"
echo "   - Health: http://localhost/actuator/health"
echo ""
echo "📋 View logs:"
echo "   docker compose -f infra/docker-compose.yml logs -f"
echo ""
echo "🛑 Stop the stack:"
echo "   docker compose -f infra/docker-compose.yml down"
echo ""

