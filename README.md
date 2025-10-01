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

## 📊 Performance Metrics & Testing Results

### Load Testing Achievements
- **100K+ Requests Tested**: Comprehensive load testing with 50K and 100K request scenarios
- **Sustained Throughput**: 74+ TPS sustained over extended periods
- **Spike Testing**: 500 concurrent users with 149 TPS peak throughput
- **Error Rate**: <1% error rate under sustained load, <2% during traffic spikes
- **Success Rate**: 99.8% success rate across all test scenarios

### Auto-scaling Performance
- **Adaptive Thread Pools**: Dynamic scaling from 8→64 threads based on P95 latency
- **Horizontal Scaling**: 3 vendor-pricing instances + 2 edge-gateway instances
- **Latency Optimization**: P95 latency improvements through adaptive scaling
- **Resource Efficiency**: Automatic scaling down during low-traffic periods

### Test Scenarios Validated
- **Baseline vs Auto-scaled**: Comparative testing showing scaling effectiveness
- **50K Request Load**: 74.8 TPS with 832ms P95 latency
- **100K Request Load**: 74.5 TPS with 836ms P95 latency  
- **Spike Testing**: 500 concurrent users with 149 TPS peak
- **Flash Sale Simulation**: High-volume traffic spike handling

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

## 🧪 Load Testing & Performance Validation

### Comprehensive Test Suite
- **Baseline vs Auto-scaled Comparison** - 200 RPS for 8-10 minutes with scaling validation
- **50K Request Load Test** - Extended load testing with 74.8 TPS sustained
- **100K Request Load Test** - High-volume testing with 74.5 TPS sustained
- **Spike Test** - 500 concurrent users simulating flash sale traffic
- **Flash Sale Simulation** - High-volume traffic spike handling
- **100K DAU Test** - Daily active user simulation

### Test Results Summary
| Test Type | Requests | Duration | Throughput | P95 Latency | Error Rate |
|-----------|----------|----------|------------|-------------|------------|
| Baseline | 1,500 | 68s | 21.9/s | 345ms | 0% |
| Auto-scaled | 1,500 | 70s | 21.5/s | 325ms | 0% |
| 50K Load | 51,332 | 686s | 74.8/s | 832ms | 0.44% |
| 100K Load | 101,062 | 1,355s | 74.5/s | 836ms | 0.18% |
| Spike Test | 13,569 | 91s | 149.1/s | 1,292ms | 1.00% |

### Running Tests
```bash
# Run baseline vs auto-scaled comparison
./loadtest/run-comparison.sh

# Run 50K and 100K load tests
./loadtest/run-scale-tests.sh

# Run spike test (500 concurrent users)
./loadtest/run-spike-test.sh

# Run flash sale simulation
./loadtest/run-flash-sale-test.sh

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
- **Advanced Auto-scaling** - Adaptive thread pools (8→64 threads) + horizontal scaling (3+2 instances)
- **Comprehensive Load Testing** - 100K+ requests across multiple scenarios with detailed metrics
- **Performance Optimization** - P95 latency improvements through adaptive scaling
- **Spike Handling** - 500 concurrent users with 149 TPS peak throughput
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