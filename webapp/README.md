# Tasklist Backend

> **Notice:** Make sure to have [docker](https://docs.docker.com/install/)
> and [docker-compose](https://docs.docker.com/compose/install/) installed
> in your machine

## Build the backend

To build the backend you can use maven:

```
mvn clean install
```

This command runs also all the test suite, that you can skip using the
option `-DskipTests=true`.

## Run modules separately

In case of high load you may need to scale importing of data from Zeebe and archiving of finished instances (this can influence UI performance).
In order to achieve this you can run any of the modules separately: Webapp, Importer and Archiver.

For this you can use following configuration parameters:
* `zeebe.tasklist.importerEnabled`: when `true` will include the Importer in current run, default: true
* `zeebe.tasklist.webappEnabled`: when `true` will include the Webapp in current run, default: true
* `zeebe.tasklist.archiverEnabled`: when `true` will include the Archiver in current run, default: true
* `zeebe.tasklist.clusterNode.partitionIds`: array of Zeebe partition ids, this Importer (or Archiver) node must be responsible for, default: empty array, meaning all partitions data is loaded
* `zeebe.tasklist.clusterNode.nodeCount`: total amount of Importer (or Archiver) nodes in cluster
* `zeebe.tasklist.clusterNode.currentNodeId`: id of current Importer (or Archiver) node, starting from 0

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
