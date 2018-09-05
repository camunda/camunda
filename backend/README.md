# Operate Backend

> **Notice:** Make sure to have [docker](https://docs.docker.com/install/)
> and [docker-compose](https://docs.docker.com/compose/install/) installed
> in your machine

# Running the tests

To run the tests you can use maven:

```
mvn clean install
```

This command runs also all the test suite, that you can skip using the
option `-DskipTests=true`.

# Standalone data generator

```
cd backend

mvn clean compile exec:java -Dexec.mainClass="org.camunda.operate.util.StandaloneDataGenerator" -Dexec.arguments="localhost:51015,default-topic" -DskipTests -P develop
```
