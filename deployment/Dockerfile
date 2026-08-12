FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle main.gradle gradle.properties lombok.config ./
COPY applications/ applications/
COPY domain/ domain/
COPY infrastructure/ infrastructure/

RUN chmod +x gradlew \
    && ./gradlew --no-daemon --no-watch-fs :app-service:bootJar -x test

FROM eclipse-temurin:21-jre-alpine AS runtime

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"

RUN addgroup -S app \
    && adduser -S app -G app \
    && mkdir -p /app /tmp \
    && chown -R app:app /app /tmp

WORKDIR /app

COPY --from=builder --chown=app:app /workspace/applications/app-service/build/libs/*.jar /app/app.jar

USER app
EXPOSE 8080
VOLUME /tmp

ENTRYPOINT ["/bin/sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
