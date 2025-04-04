# Tasklist Backend

> **Notice:** Make sure to have [docker](https://docs.docker.com/install/)
> and [docker-compose](https://docs.docker.com/compose/install/) installed
> in your machine

## Build the backend

To build the backend you can use maven:

```
../../mvnw clean install
```

This command runs also all the test suite, that you can skip using the
option `-DskipTests=true`.

## Environment variables

In order to Setup the Webapp locally, after to start the docker images (See Tasklist READ.ME), it is necessary configurate the env. variables over the Spring Profile, this is the minimum requirement:

* `CAMUNDA_TASKLIST_BACKUP_REPOSITORYNAME=test;`
* `CAMUNDA_TASKLIST_ELASTICSEARCH_URL=http://localhost:9200;`
* `CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=localhost:26500;`
* `CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL=http://localhost:9200;`
* `SERVER_PORT=8082;SPRING_PROFILES_ACTIVE=identity-auth;`
* `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI=http://localhost:18080/auth/realms/camunda-platform;`
* `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI=http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs`

This is the minimum requirement, in case to setup the identity:

* `CAMUNDA_TASKLIST_IDENTITY_AUDIENCE=tasklist-api;`
* `CAMUNDA_TASKLIST_IDENTITY_BASEURL=http://localhost:8084;`
* `CAMUNDA_TASKLIST_IDENTITY_CLIENT_ID=tasklist;`
* `CAMUNDA_TASKLIST_IDENTITY_CLIENT_SECRET=the-cake-is-alive;`
* `CAMUNDA_TASKLIST_IDENTITY_ISSUERBACKENDURL=http://localhost:18080/auth/realms/camunda-platform;`
* `CAMUNDA_TASKLIST_IDENTITY_ISSUERURL=http://localhost:18080/auth/realms/camunda-platform;`
* `CAMUNDA_TASKLIST_IDENTITY_RESOURCE_PERMISSIONS_ENABLED=true;`

Do not forget to make sure that all services are up on Docker.

## Run modules separately

In case of high load you may need to scale importing of data from Zeebe and archiving of finished instances (this can influence UI performance).
In order to achieve this you can run any of the modules separately: Webapp, Importer and Archiver.

For this you can use following configuration parameters:
* `camunda.tasklist.importerEnabled`: when `true` will include the Importer in current run, default: true
* `camunda.tasklist.webappEnabled`: when `true` will include the Webapp in current run, default: true
* `camunda.tasklist.archiverEnabled`: when `true` will include the Archiver in current run, default: true
* `camunda.tasklist.clusterNode.partitionIds`: array of Zeebe partition ids, this Importer (or Archiver) node must be responsible for, default: empty array, meaning all partitions data is loaded
* `camunda.tasklist.clusterNode.nodeCount`: total amount of Importer (or Archiver) nodes in cluster
* `camunda.tasklist.clusterNode.currentNodeId`: id of current Importer (or Archiver) node, starting from 0

It's enough to configure either `partitionIds` or pair of `nodeCount` and `currentNodeId`.

To further parallelize archiving within one node, following configuration parameter can be used:

`archiver.threadsCount:`: number of threads in which archiving will be run. Default: 1.

## Demo data

There are two sets of data, defined in two different Spring profiles:

- `usertest-data`: data for user tests
- `dev-data`: development data (includes data for user tests plus more)

Ways to activated profiles:

- when running via `make env-up` or `docker-compose`: edit `docker-compose.yml`, section `services.tasklist.environment` (always leave `dev` profile active)

```text
- SPRING_PROFILES_ACTIVE=dev,dev-data,auth
```

- when running from distribution via `tasklist` shell script or `tasklist.bat`:

```text
JAVA_OPTS=-Dspring.profiles.active=dev-data ./tasklist
or
JAVA_OPTS=-Dspring.profiles.active=dev-data ./tasklist.bat
```

## GraphQL

The GraphQL endpoint is `/graphql`.

For development, the inspection tool GraphQL Playground is embedded and can be accesses under http://localhost:8080/playground.

# Webapp module

This module contains:
* GraphQL API
* Management (Actuator) endpoints: Usage metrics and [Backup API](docs/backup.md)
