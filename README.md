# DealCart+ - Scalable E-commerce Microservices Platform

A production-ready, horizontally scalable e-commerce platform built with Java 21, gRPC, Spring Boot, and React. Features auto-scaling, load balancing, distributed transactions, and comprehensive CI/CD pipeline.

## 🏗️ Architecture

### Microservices
- **Edge Gateway** - Spring Boot API gateway with SSE streaming and rate limiting
- **Vendor Pricing** - gRPC service with adaptive thread pool auto-scaling
- **Checkout Service** - SAGA pattern for distributed transaction management
- **Vendor Mock** - Simulated vendor APIs for testing and development
- **Web UI** - React/Next.js frontend with Tailwind CSS

### Infrastructure
- **Docker Compose** - Container orchestration with horizontal scaling
- **Caddy** - Load balancer and reverse proxy
- **AWS EC2** - Cloud deployment platform
- **AWS S3** - Receipt storage and data persistence
- **GitHub Actions** - CI/CD pipeline with automated deployment

## 🚀 Key Features

### Scalability & Performance
- **Horizontal Scaling** - Docker Compose replicas with load balancing
- **Adaptive Thread Pools** - Dynamic scaling based on P95 latency
- **Load Testing** - JMeter tests for 100K+ requests with 99.8% success rate
- **Auto-scaling** - CPU/memory-based scaling with realistic thresholds

### Distributed Systems
- **gRPC Communication** - High-performance inter-service communication
- **SAGA Pattern** - Distributed transaction management with rollback
- **Promise Graph** - Asynchronous workflow orchestration
- **Request Tracing** - End-to-end request ID propagation

### Production Features
- **Rate Limiting** - Token bucket algorithm for API protection
- **Health Checks** - Comprehensive service monitoring
- **Metrics API** - Real-time system and traffic metrics
- **SSE Streaming** - Real-time quote streaming to frontend

## 📊 Performance Metrics

- **Throughput**: 200+ RPS sustained
- **Latency**: P95 ~250ms, P99 ~500ms
- **Success Rate**: 99.8% under load
- **Auto-scaling**: 1→5 instances based on traffic
- **Load Testing**: 100K+ requests validated

## 🛠️ Technology Stack

### Backend
- **Java 21** - Latest LTS with modern features
- **Spring Boot 3.3** - Microservices framework
- **gRPC** - High-performance RPC communication
- **Protocol Buffers** - Efficient serialization
- **Docker** - Containerization and orchestration

### Frontend
- **React 18** - Modern UI framework
- **Next.js 14** - Full-stack React framework
- **Tailwind CSS** - Utility-first CSS framework
- **TypeScript** - Type-safe JavaScript

### Infrastructure
- **Docker Compose** - Multi-container orchestration
- **Caddy** - Modern web server and load balancer
- **AWS EC2** - Cloud compute platform
- **AWS S3** - Object storage
- **GitHub Actions** - CI/CD automation

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Node.js 18+
- Docker & Docker Compose
- Maven 3.8+

### Local Development
```bash
# Clone the repository
git clone https://github.com/prajwalun/dealcart.git
cd dealcart

# Start all services
docker compose -f infra/docker-compose.yml up -d

# Verify services are running
docker compose -f infra/docker-compose.yml ps

# Test the application
curl "http://localhost/api/search?q=headphones"
curl "http://localhost/actuator/health"
```

### Frontend Development
```bash
# Navigate to web UI
cd web-ui

# Install dependencies
npm install

# Start development server
npm run dev
```

## 🧪 Load Testing

### JMeter Test Plans
- **Baseline Test** - 200 RPS for 8-10 minutes
- **Auto-scaled Test** - Dynamic scaling validation
- **Spike Test** - 500 concurrent users
- **100K DAU Test** - High-volume simulation

### Running Tests
```bash
# Run baseline vs auto-scaled comparison
./loadtest/run-comparison.sh

# Run spike test
./loadtest/run-spike-test.sh

# Run 100K DAU simulation
./loadtest/run-100k-dau-test.sh
```

## ☁️ AWS Deployment

### Infrastructure Setup
1. **EC2 Instance** - Ubuntu 22.04 LTS (t3.micro)
2. **S3 Bucket** - Receipt storage
3. **IAM Role** - S3 access permissions
4. **Security Groups** - HTTP/HTTPS and SSH access

### Automated Deployment
```bash
# Deploy via GitHub Actions
# 1. Push to main branch
git push origin main

# 2. Run deploy workflow
# Go to Actions tab → Run "Deploy" workflow

# 3. Verify deployment
curl "http://<EC2_IP>/api/search?q=headphones"
```

## 📈 Monitoring & Observability

### Health Endpoints
- **Gateway Health**: `http://localhost/actuator/health`
- **Service Metrics**: `http://localhost:10100/metrics`
- **Load Balancer**: Caddy health checks

### Key Metrics
- **RPS** - Requests per second
- **P95/P99 Latency** - Response time percentiles
- **Error Rate** - Failed request percentage
- **CPU/Memory** - System resource utilization
- **Thread Pool** - Active threads and queue size

## 🔧 Configuration

### Environment Variables
```bash
# Docker Compose
IMAGE_PREFIX=ghcr.io/username/dealcart

# Auto-scaling
ADAPTIVE_MIN=8
ADAPTIVE_MAX=64
TARGET_P95_MS=250
LOWER_P95_MS=200

# S3 Configuration
S3_BUCKET=dealcart-bucket
AWS_REGION=us-west-2
```

### Scaling Configuration
```yaml
# docker-compose.yml
services:
  vendor-pricing:
    deploy:
      replicas: 3
  edge-gateway:
    deploy:
      replicas: 2
```

## 🏆 Portfolio Highlights

### Technical Achievements
- **Microservices Architecture** - 5 independent services with gRPC communication
- **Auto-scaling** - Dynamic thread pool scaling based on latency metrics
- **Load Testing** - Comprehensive JMeter test suite for 100K+ requests
- **CI/CD Pipeline** - GitHub Actions with automated Docker builds and deployment
- **Distributed Transactions** - SAGA pattern implementation for data consistency
- **Real-time Streaming** - SSE implementation for live quote updates

### Production Readiness
- **Horizontal Scaling** - Docker Compose replicas with load balancing
- **Health Monitoring** - Comprehensive health checks and metrics
- **Error Handling** - Graceful degradation and retry mechanisms
- **Security** - Rate limiting and request validation
- **Observability** - Request tracing and performance monitoring

## 📚 Documentation

- [Project Deep-Dive](PROJECT_DEEP_DIVE.md) - Comprehensive technical analysis
- [Load Testing Guide](docs/load-testing-explained.md)
- [Horizontal Scaling Plan](docs/horizontal-scaling-plan.md)
- [CI/CD Documentation](docs/ci-cd.md)
- [Auto-scaling Implementation](docs/PRODUCTION-SCALING.md)
- [Mock Data Catalog](docs/mock-data-catalog.md)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- gRPC team for high-performance RPC
- Docker team for containerization
- AWS for cloud infrastructure
- The open-source community for amazing tools

---

**Built with ❤️ for scalable e-commerce solutions**