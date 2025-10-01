# Use Temurin 21 JRE as base image
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the Spring Boot JAR
COPY edge-gateway/target/edge-gateway-*.jar app.jar

# Expose the default port
EXPOSE 8080

# Set environment variables with defaults
ENV PORT=8080
ENV PRICING_HOST=localhost
ENV PRICING_PORT=9100
ENV CHECKOUT_HOST=localhost
ENV CHECKOUT_PORT=9200
ENV RATE_LIMIT_QPS=100
ENV GRPC_DEADLINE_SECONDS=2

# Health check will be handled by Docker Compose in Step 7

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

