# The Camunda Optimize Performance Test Suite

This testsuite allows running different kinds of performance tests against Optimize.

**Table of Contents:**

* [Data Generation](#data-generation)
* [The Heat Map](#the-heat-map)
* [The Correlation Discovery](#correlation-discovery)

> **Design Rationale**: This testsuite does not try to produce absolute numbers. The goal is not to produce numbers that show "how fast the optimize is". On the contrary, the idea is to produce relative numbers that can be compared over time. For instance, the heat map tests allow us to get a sense of whether a certain change to the codebase made the query for the heat map in optimize faster or slower compared to the numbers we were getting before.

## Data Generation

The data generation creates a certain amount of events and adds them to elasticsearch. The data generation is started automatically, when you execute the tests. 

You have the following options to configure the data generation:

* Adapt the amount of generated data [here](https://github.com/camunda/camunda-optimize/blob/master/qa/performance-tests/pom.xml#L126) (default is 1 000 000).
* Change the number of threads being used to generate data [here](https://github.com/camunda/camunda-optimize/blob/master/qa/performance-tests/pom.xml#L125) (default is 2).

## The Heat Map

To run the heatmap tests you can just run the following command:

```
mvn clean verify -Pservice-perf-tests
```

It is possible to configure the max waiting time until the fetching of the heatmap should be finished [here](https://github.com/camunda/camunda-optimize/blob/master/qa/performance-tests/pom.xml#L124) (default is 1 000 ms).

### Correlation Discovery

