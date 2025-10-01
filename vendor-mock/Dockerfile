# Use Temurin 21 JRE as base image
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the fat JAR
COPY vendor-mock/target/vendor-mock-*.jar app.jar

# Expose the default port
EXPOSE 9101

# Set environment variables with defaults
ENV VENDOR_NAME=MockVendor
ENV PORT=9101

# Health check using netstat (available in alpine)
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD netstat -an | grep :9101 || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
