FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl cabextract \
    && curl -fL --retry 3 https://downloads.sourceforge.net/corefonts/arial32.exe -o /tmp/arial32.exe \
    && echo "85297a4d146e9c87ac6f74822734bdee5f4b2a722d7eaa584b7f2cbf76f478f6  /tmp/arial32.exe" | sha256sum -c - \
    && mkdir -p /usr/local/share/fonts/arial \
    && cabextract -q -d /usr/local/share/fonts/arial /tmp/arial32.exe \
    && rm /tmp/arial32.exe && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/transferencia-1.0.0.jar /app/app.jar
ENTRYPOINT ["java", "-Xmx192m", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
