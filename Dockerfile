FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /build/target/similar-products-*.jar app.jar
EXPOSE 5000

# Netty reaches for Unsafe and native libraries on startup. Declaring the access keeps the JDK 25
# startup output clean without changing how the application behaves.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow", "-jar", "app.jar"]
