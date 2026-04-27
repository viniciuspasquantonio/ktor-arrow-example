# Estágio 1: build
FROM gradle:8-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle buildFatJar --no-daemon

# Estágio 2: runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
