# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS builder
LABEL author="MartinsT"

WORKDIR /app

# Copy Gradle wrapper and build configuration first
# so dependency resolution can be cached separately
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# Now copy the application source
COPY src src

# Build the Spring Boot executable JAR
RUN ./gradlew bootJar --no-daemon


# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]