FROM openjdk:8u151-jre-alpine3.7
COPY ./backend/target/*-exec.jar /usr/app/camunda-operate.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/app/camunda-operate.jar"]
