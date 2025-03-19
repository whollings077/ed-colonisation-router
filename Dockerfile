FROM eclipse-temurin:23-jdk AS builder
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
COPY src/main/resources/even_smaller_stations.json /app/even_smaller_stations.json
COPY src/main/resources/commodity_data.txt /app/commodity_data.txt
COPY src/main/resources/tradeplanner.yml /app/config/tradeplanner.yml
COPY src/main/resources/application.yml /app/config/application.yml

ENV SPRING_CONFIG_LOCATION=file:/app/config/application.yml,file:/app/config/tradeplanner.yml
EXPOSE 8080

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", "-jar", "app.jar"]

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1