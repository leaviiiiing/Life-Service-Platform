FROM eclipse-temurin:8-jre

WORKDIR /app

COPY target/life-service-platform-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar","--spring.profiles.active=docker"]
