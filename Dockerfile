# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependency resolution before copying source
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd -r -U appuser

COPY --from=build /build/target/*.jar app.jar

ENV FIREBASE_ENABLED=true \
    FIREBASE_CREDENTIALS_PATH=/etc/secrets/firebase-adminsdk.json

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
