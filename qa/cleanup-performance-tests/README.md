# Camunda Optimize Cleanup Performance Test Suite

This test suite verifies non-functional requirements of the history
cleanup in terms of duration given a static data set.

The tests first execute the import from the engine but don't assert a duration for it.
Afterwards the cleanup is performed and it's duration is checked
to be below/equal of a certain limit configured in [properties file](./src/test/resources/static-cleanup-test.properties)

## Execute Optimize Cleanup Performance Tests

Prerequisites:
* A Camunda Engine is running. Ideally with a lot of data to put a lot of stress on the cleanup
* An Elasticsearch instance is running.
* The tests are correctly configured in the [properties file](./src/test/resources/static-cleanup-test.properties)
to be able to connect to the Elasticsearch and the engine.

There are two test suites built into this test module, separated using maven profiles:
1. Test Camunda Platform engine data cleanup (decision and process instance data)
They can be run using the maven profile `engine-cleanup-performance`:

```
mvn -Pengine-cleanup-performance clean test
```
2. Test Ingested Event and Event Based Process data cleanup
They can be run using the maven profile `event-cleanup-performance`:

```
mvn -Pevent-cleanup-performance clean test
```