# Reliability testing

We define reliability testing as a type of software testing and practice that validates system performance and reliability. It can thus be done over time and with injection failure scenarios (injecting chaos).

The aim of such testing practices is to ensure that [2]

* Issues are identified before customers see them (proactively finding bugs)
* The system can run fault-free / reliably over a longer / specific period of time for its intended purpose and specific environment
    * to ensure that the system will be able to meet the needs of its users over the long term
* Failures are handled gracefully, validating reliability even after error cases
* Performance stays within its bounds, even over long periods of time.

The last points are especially interesting, as they allow for identifying issues that are with normal unit or integration tests not discoverable. For example, memory leaks or problems in distribution systems, etc.

Reliability testing consists of several sub-testing practices (depending on the context/area, company, this might vary).

While several resources ([1], [2]) cover feature or regression testing as one part of reliability testing, we see this as part of our automated acceptance testing done by Engineers and/or QA. See also related documentation about this here.

We focus as part of the reliability testing on topics like load testing (stress and endurance testing), and recovery testing (done by chaos experiments) 

[1]: https://www.gremlin.com/blog/reliability-testing-definition-history-methods-and-examples
[2]: https://testsigma.com/guides/reliability-testing/ 
[3]: https://www.geeksforgeeks.org/software-testing-reliability-testing/ 

## Load testing

Load testing, or previously called "benchmarks" is a practice that was introduced in early 2019. As of today, we run load tests on a regular basis.

### General Goals

With our load test, we pursue the following goals

* Build up confidence in the system's reliability
* Discover failures when running over longer periods of time
    * Memory leaks
    * Performance degradation
    * Business logic errors
    * Distributed system failures
* Discover configuration/set up issues in different environments, SM/SaaS

### Setup

The setup for all of our load tests is equal for better comparability. We use a custom helm chart (zeebe-benchmark) based on our official Camunda Platform Helm chart.


We always ran load tests with a three-node cluster, configured with three partitions and a replication factor of three. Depending on the version of Camunda/Zeebe (pre 8.8), we might only deploy Zeebe Brokers and the Zeebe (standalone) gateway (with two replicas) or the single Camunda application (with an embedded gateway). To validate that our data flow pipeline is working, we are running an Elasticsearch cluster with three nodes for any Camunda test cluster.

On top of the Camunda Platform Helm Chart, the benchmark Helm Chart deploys different applications. They can be distinguished into workers and starters. The related code can be found in the Camunda mono repository.

Depending on the test variant, different process models are created and executed by the Starter and Worker applications. They only differ in configurations, which can be done by the respective zeebe-benchmark Helm chart, and their values files.

All of this is deployed in a Zeebe-maintained (as of now; 16 Jun 2025) Google Kubernetes Engine (GKE) cluster (zeebe-cluster), in its own zeebe-io Google Cloud Project (GCP). Details of the general infrastructure, which is deployed related to observability (Prometheus), can be found in the Zeebe infrastructure repository.

### Variants

#### Normal (artificial) load
A load test where we run some artificial load, ensuring that the system behaves reliably. 
