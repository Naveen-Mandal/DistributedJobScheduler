# Stage 1: Build the application
FROM maven:3-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Install common-module first
COPY common-module ./common-module
RUN mvn clean install -f common-module/pom.xml -DskipTests -B

# Build scheduler-service
COPY scheduler-service ./scheduler-service
RUN mvn clean package -f scheduler-service/pom.xml -DskipTests -B

# Stage 2: Create execution container
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/scheduler-service/target/scheduler-service-0.0.1-SNAPSHOT.jar app.jar
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
