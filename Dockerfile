# syntax=docker/dockerfile:1

# --- Build stage: compile and package the executable jar ---
# Uses the project's own Maven Wrapper so the Maven version matches local builds.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the wrapper and pom first so dependency resolution is cached as its own layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Now copy sources and build the jar (tests run via `./mvnw test`, not in the image).
COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests

# --- Runtime stage: small JRE image that just runs the jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user.
RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/event-ledger-api-*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
