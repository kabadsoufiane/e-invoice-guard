# Build multi-stage : image finale legere pour tenir dans les quotas gratuits (Render/Oracle)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Cache les dependances Maven separement du code pour accelerer les rebuilds
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Utilisateur non-root par bonne pratique de securite
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xmx400m", "-jar", "app.jar"]
