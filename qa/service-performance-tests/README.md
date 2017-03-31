# The Camunda Optimize Rest Service Performance Test Suite

This testsuite allows running different kinds of performance tests against Optimize. In particular, here you can get a feeling of how performant the rest api of optimize is by executing it against a huge amount of instances.

Basically, the tests consists of two steps: First, create a certain amount of activity instances and add those events to Elasticsearch. Second, run the query against the given data. 

**Table of Contents:**

* [Execute service performance tests](#execute-service-performance-tests)
* [Configure properties](#cConfigure-properties)

> **Design Rationale**: This testsuite does not try to produce absolute numbers. The goal is not to produce numbers that show "how fast the optimize is". On the contrary, the idea is to produce relative numbers that can be compared over time. For instance, the heat map tests allow us to get a sense of whether a certain change to the codebase made the query for the heat map in optimize faster or slower compared to the numbers we were getting before.

## Execute service performance tests

To run the performance tests you can run the following command:

```
mvn clean verify -Pservice-perf-tests
```

It is also possible to run just a specific test class like:

```
mvn clean verify -Pservice-perf-tests -Dtest=HeatmapPerformanceTest
```

To get an overview of all possible test classes, have a look [here](https://github.com/camunda/camunda-optimize/tree/master/qa/service-performance-tests/src/test/java/org/camunda/optimize/qa/performance).

You can even just run a specific test by, e.g:

```
mvn clean verify -Pservice-perf-tests -Dtest=HeatmapPerformanceTest#getFrequencyHeatmapWithoutFilter
```


## Configure properties

You have the following options to configure the data generation:

* Adapt the amount of generated data [here](https://github.com/camunda/camunda-optimize/blob/master/qa/service-performance-tests/pom.xml#L133) (default is 1 000 000).
* Change the number of threads being used to generate data [here](https://github.com/camunda/camunda-optimize/blob/master/qa/service-performance-tests/pom.xml#L132) (default is 2).
* Change the maximal time allowed to execute the query [here](https://github.com/camunda/camunda-optimize/blob/master/qa/service-performance-tests/pom.xml#L131) (default is 1000 ms).


