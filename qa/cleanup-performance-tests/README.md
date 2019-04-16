# Camunda Optimize Cleanup Performance Test Suite - Static

This test suite verifies non-functional requirements of the history
cleanup in terms of duration given a static data set.

The tests first execute the import from the engine but don't assert a duration for it.
Afterwards the cleanup is performed and it's duration is checked
to be below/equal of a certain limit configured in [properties file](./src/test/resources/static-cleanup-test.properties)

## Execute query performance tests

Prerequisites:
* A Camunda Engine is running. Ideally with a lot of data to put a lot of stress on the cleanup
* An Elasticsearch instance is running.
* The tests are correctly configured in the [properties file](./src/test/resources/static-cleanup-test.properties)
to be able to connect to the Elasticsearch and the engine.

To start the tests use the following command

```
mvn -Pcleanup-performance-test clean test
```