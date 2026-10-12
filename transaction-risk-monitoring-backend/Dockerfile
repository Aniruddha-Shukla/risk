# ---- Build: compile and package the fat JAR ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Run: slim JRE image ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*-jar-with-dependencies.jar app.jar
# Render sets PORT at runtime; 8080 is the local default
EXPOSE 8080
# Keep heap inside Render free tier's 512 MB
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
