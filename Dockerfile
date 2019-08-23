FROM openjdk:8u191-jre-alpine3.9
COPY ./backend/target/*-exec.jar /usr/app/camunda-operate.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/app/camunda-operate.jar"]