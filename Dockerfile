FROM openjdk:8-alpine

RUN mkdir /app
COPY build/libs/*.jar /app/

WORKDIR /app

CMD ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/pickapic-1.0-SNAPSHOT-all.jar"]