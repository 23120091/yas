FROM eclipse-temurin:25-jre-alpine
COPY target/sampledata*.jar app.jar
COPY images/sample /sample-images
ENTRYPOINT ["java", "-jar", "/app.jar"]