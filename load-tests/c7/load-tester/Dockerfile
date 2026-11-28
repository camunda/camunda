# Build context: repository root. docker build -f load-tester/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY engine/pom.xml engine/
COPY load-tester/pom.xml load-tester/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl load-tester -am dependency:go-offline -DexcludeReactor=true || true
COPY engine/src engine/src
COPY load-tester/src load-tester/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl load-tester -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/load-tester/target/app.jar app.jar
EXPOSE 8080 9600
# Container-aware heap sizing; override with JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
