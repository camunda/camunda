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

# Demo data

There are two sets of data, defined in two different Spring profiles:

- `usertest-data`: data for user tests
- `dev-data`: development data (includes data for user tests plus more)

Ways to activated profiles:

- when running via `make env-up` or `docker-compose`: edit `docker-compose.yml`, section `services.operate.environment` (always leave `dev` profile active)
```text
- SPRING_PROFILES_ACTIVE=dev,dev-data
```
- when running from distribution via `operate` shell script or `operate.bat`:
```text
JAVA_OPTS=-Dspring.profiles.active=dev-data ./operate
or 
JAVA_OPTS=-Dspring.profiles.active=dev-data ./operate.bat
```