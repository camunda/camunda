FROM maven:3.5.3-jdk-8-alpine AS build
COPY . /usr/src/app/
RUN mvn -f /usr/src/app/pom.xml clean package -P -docker,develop -DskipTests=true

FROM openjdk:8u151-jre-alpine3.7
COPY --from=build /usr/src/app/backend/target/*.jar /usr/app/camunda-operate.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/app/camunda-operate.jar"]
