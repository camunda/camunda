FROM maven:3-jdk-8-alpine as builder
WORKDIR /usr/src/app
COPY src/ src/
COPY pom.xml pom.xml
RUN mvn clean package -DskipTests

FROM openjdk:8-jre-alpine as starter
USER nobody
CMD java $JAVA_OPTIONS -jar app.jar
COPY --from=builder /usr/src/app/target/starter.jar /app.jar

FROM openjdk:8-jre-alpine as worker
USER nobody
CMD java $JAVA_OPTIONS -jar app.jar
COPY --from=builder /usr/src/app/target/worker.jar /app.jar

FROM openjdk:8-jre-alpine as all-in-one
USER root
RUN apk update && apk add libc6-compat
USER nobody
CMD java $JAVA_OPTIONS -jar app.jar
COPY --from=builder /usr/src/app/target/all-in-one.jar /app.jar
