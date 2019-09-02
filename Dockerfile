FROM openjdk:11-jre
COPY ./webapp/target/*-exec.jar /usr/app/camunda-operate.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/app/camunda-operate.jar"]
