# Use Temurin 21 JRE as base image
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the fat JAR
COPY target/vendor-pricing-*.jar app.jar

# Expose the default port
EXPOSE 9100

# Set environment variables with defaults
ENV PORT=9100
ENV VENDORS="localhost:9101:FastVendor,localhost:9102:SlowVendor"

# Health check using netstat (available in alpine)
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD netstat -an | grep :9100 || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
