# Use Temurin 21 JRE as base image
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the fat JAR
COPY checkout/target/checkout-*.jar app.jar

# Expose the default port
EXPOSE 9200

# Set environment variables with defaults
ENV PORT=9200

# Health check will be handled by Docker Compose in Step 7

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

