FROM maven:3.5.3-jdk-8-alpine AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package -P -docker -DskipTests=true

FROM openjdk:8u151-jre-alpine3.7
COPY --from=build /usr/src/app/target/operate-backend-1.0.0-SNAPSHOT.jar /usr/app/operate-backend-1.0.0-SNAPSHOT.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/app/operate-backend-1.0.0-SNAPSHOT.jar"]
