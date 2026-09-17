# syntax=docker/dockerfile:1

# build
FROM  eclipse-temurin:25-jdk AS build
WORKDIR /build

COPY gradlew ./
COPY gradle gradle
COPY settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# исходники
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

#запуск
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN useradd --system --uid 10001 appuser
COPY --from=build /build/build/libs/*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]