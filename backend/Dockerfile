# ---- Build stage: compile the Spring Boot backend ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- Runtime stage: slim JRE ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/air-quality-twin-*.jar app.jar
EXPOSE 8080
# Config comes from environment variables (DB_HOST, DB_PORT, etc.) - no secrets baked in.
ENTRYPOINT ["java", "-jar", "app.jar"]
