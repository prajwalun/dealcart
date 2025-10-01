# Use Temurin 21 JRE as base image
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the fat JAR
COPY vendor-pricing/target/vendor-pricing-*.jar app.jar

# Expose the default port
EXPOSE 9100

# Set environment variables with defaults
ENV PORT=9100
ENV VENDORS="localhost:9101:FastVendor,localhost:9102:SlowVendor"

# Health check will be handled by Docker Compose in Step 7

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
