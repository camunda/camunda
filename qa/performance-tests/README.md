# The Camunda Optimize Performance Test Suite

This testsuite allows running different kinds of performance tests against Optimize.

**Table of Contents:**

* [Data Generation](#data-generation)
* [The Heat Map](#the-heat-map)
* [The Correlation Discovery](#correlation-discovery)

> **Design Rationale**: This testsuite does not try to produce absolute numbers. The goal is not to produce numbers that show "how fast the optimize is". On the contrary, the idea is to produce relative numbers that can be compared over time. For instance, the heat map tests allow us to get a sense of whether a certain change to the codebase made the query for the heat map in optimize faster or slower compared to the numbers we were getting before.

## Data Generation

The data generation creates a certain amount of events and adds them to elasticsearch. As soon as you run the following command:

```
mvn clean verify -Pperf-test
```

the data generation is executed automatically. The default amount of generated events is 1 000 000, but it is possible to adapt that value by changing [this](https://github.com/camunda/camunda-optimize/blob/master/qa/performance-tests/pom.xml#L105) argument.

## The Heat Map


### Correlation Discovery

