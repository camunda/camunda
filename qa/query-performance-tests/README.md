# The Camunda Optimize Query Performance Test Suite

This testsuite monitors how performant the rest api of optimize is 
by executing all possible report queries against a huge amount of instances.

Basically, the tests consists of two steps: First, import all data from
the Camunda engine. Second, run every query against the given data. 

## Execute query performance tests

Prerequisites:
* A Camunda Engine is running. Ideally with a lot of data to put
a lot of stress on the query.
* An Elasticsearch instance is running.
* The tests are correctly configured in the [properties file](./src/test/resources/query-performance.properties)
to be able to connect to the Elasticsearch and the engine.

If the prerequisites are fulfilled run the performance tests 
by executing the following command:

```
mvn clean verify -Pquery-performance-tests
```
