# Use an appropriate OpenJDK base image
FROM eclipse-temurin:11-jre-focal

# Set working directory
WORKDIR /app

# Copy the fat JAR from the Maven build context
# The shaded JAR name is based on <artifactId>-<version>-shaded.jar
COPY target/realtime-index-system-1.0-SNAPSHOT-shaded.jar app.jar

# Environment variables for configuration (with defaults set in the application)
ENV KAFKA_BOOTSTRAP_SERVERS="kafka:9092"
ENV REDIS_HOST="redis"
ENV REDIS_PORT="6379"
# Add any other environment variables your main class might need
# For example, if you want to control logging level via env var:
# ENV JAVA_LOGGING_LEVEL="INFO" 

# Expose any ports if your application listens on them (not typical for a simple Kafka Streams app)
# EXPOSE 8080 

# Command to run the application
# The main class is already specified in the MANIFEST.MF of the shaded JAR
ENTRYPOINT ["java", "-jar", "app.jar"]
