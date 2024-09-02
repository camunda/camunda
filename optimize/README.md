# Camunda Optimize

Camunda Optimize is an extension to Camunda Platform for enterprise customers,
that provides continuous monitoring and insights about your deployed
business processes.

* [Issue Tracker](https://github.com/orgs/camunda/projects/101/views/1)
* [Documentation](https://docs.camunda.io/docs/components/optimize/what-is-optimize/)

## Building Optimize

Apache Maven 3 and Java JDK 21 are prerequisites for building Camunda
Optimize. Once you have set up Java and Maven, run

```
mvn clean install
```

And if you don't want to run the tests

```
mvn clean install -DskipTests
```

**On Windows** make sure you run this in a command line as Administrator

- as some npm modules need to be able to create symlinks.

After the build is completed, you will find the distributions under ```
optimize-distro/target ```

## Frontend

Learn how to build, debug, test and more in the front-end in
the [Front-End README](./client/README.md)

## Backend

Learn how to build, debug, test and more in the back-end in the [Back-End README](backend/README.md)

## Development Setup

### Docker

You can run Optimize in C8SM mode (Self-managed C8 Engine) by running the following command

```
docker-compose -f docker-compose.ccsm-with-optimize.yml up -d
```

or optionally the C8SM stack without Optimize using:

```
docker-compose -f docker-compose.ccsm-without-optimize.yml up -d
```

This will start all the components of Identity, Elasticsearch, Zeebe, and Optimize. Optimize will be
available at localhost:8090.

Optimize then needs to be started in C8SM mode, and with the following environment variables:

```
SPRING_PROFILES_ACTIVE=ccsm;
CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL=[Identity host]
CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID=optimize;
CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET=[must match the one in docker-compose.ccsm.yml]
CAMUNDA_OPTIMIZE_IDENTITY_AUDIENCE=[audience]
```

## F.A.Q

# Docker is telling me that I do not have the rights to pull the images

You probably did not login in Camunda's private Docker registry. Since we
are using enterprise images that are available only to paying customer it
is necessary to authenticate with:

```
docker login registry.camunda.cloud
```

You can use your LDAP credentials to continue the authentication.

## Running integration tests locally

You can run locally integration tests using maven. The command to run the
tests is the following:

```
mvn -Pit -pl backend -am clean install
```

## Debugging integration tests locally with IntelliJ

You can use the debugger integrated within IntelliJ to go through any integration test
directly. The only prerequisite is that your IntelliJ is already configured to run or debug
Optimize.

From a fresh environment (i.e., no docker containers are running), all you need is the containers
defined in the default `docker-compose.yaml` file:

```bash
$ docker compose up -d
```

Once the containers are running, you need to prepare the test environment:

```bash
$ mvn pre-integration-test -Pit -pl backend -am -Dskip.fe.build
```

After this, you are free to set breakpoints in the code and debug any integration test using
IntelliJ.
