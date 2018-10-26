FROM openjdk:8u181-jre-alpine3.8
COPY ./backend/target/*-exec.jar /usr/app/camunda-operate.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/app/camunda-operate.jar"]
