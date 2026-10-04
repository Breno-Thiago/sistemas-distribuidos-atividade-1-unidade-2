FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends fonts-dejavu-core && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/transferencia-1.0.0.jar /app/app.jar
ENTRYPOINT ["java", "-Xmx192m", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
