# Stage 1: Build the application
FROM maven:3-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Install common-module first
COPY common-module ./common-module
RUN mvn clean install -f common-module/pom.xml -DskipTests -B

# Build worker-service
COPY worker-service ./worker-service
RUN mvn clean package -f worker-service/pom.xml -DskipTests -B

# Stage 2: Create execution container
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/worker-service/target/worker-service-0.0.1-SNAPSHOT.jar app.jar
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
