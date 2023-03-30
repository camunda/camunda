# Zeebe - Workflow Engine for Microservices Orchestration

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.camunda.zeebe/camunda-zeebe/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.camunda.zeebe/camunda-zeebe)

Zeebe provides visibility into and control over business processes that span multiple microservices. It is the engine that powers [Camunda Platform 8](https://camunda.com/platform/zeebe/).

**Why Zeebe?**

* Define processes visually in [BPMN 2.0](https://www.omg.org/spec/BPMN/2.0.2/)
* Choose your programming language
* Deploy with [Docker](https://www.docker.com/) and [Kubernetes](https://kubernetes.io/)
* Build processes that react to messages from [Kafka](https://kafka.apache.org/) and other message queues
* Scale horizontally to handle very high throughput
* Fault tolerance (no relational database required)
* Export process data for monitoring and analysis
* Engage with an active community

[Learn more at camunda.com](https://camunda.com/platform/zeebe/)

## Release Lifecycle

Our release cadence within major releases is a minor release every six months, with an alpha release on each of the five months between minor releases. Releases happen on the second Tuesday of the month, Berlin time (CET).

Minor releases are supported with patches for eighteen months after their release.

Here is a diagram illustrating the lifecycle of minor releases over a 27-month period:

```
2022                       2023                                2024
Ap Ma Ju Ju Au Se Oc No De Ja Fe Ma Ap Ma Ju Ju Au Se Oc No De Ja Fe Ma Ap Ma Ju
8.0--------------------------------------------------|
                  8.1--------------------------------------------------|
                                    8.2-----------------------------------------
                                                      8.3-----------------------
                                                                        8.4-----
1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27
```

Here is a diagram illustrating the release schedule of the five alpha releases prior to an upcoming minor release over a 7-month period:

```
2022                                2023
Oct       Nov          Dec          Jan          Feb          Mar          Apr
8.1-----------------------------------------------------------------------------
          8.2-alpha1   8.2-alpha2   8.2-alpha3   8.2-alpha4   8.2-alpha5   8.2-- 
1         2            3            4            5            6            7
```

## Status

To learn more about what we're currently working on, check the [GitHub issues](https://github.com/camunda/zeebe/issues?q=is%3Aissue+is%3Aopen+sort%3Aupdated-desc) and the [latest commits](https://github.com/camunda/zeebe/commits/main).

## Helpful Links

* [Releases](https://github.com/camunda/zeebe/releases)
* [Pre-built Docker images](https://hub.docker.com/r/camunda/zeebe/tags?page=1&ordering=last_updated)
* [Building Docker images for other platforms](/docs/building_docker_images.md)
* [Blog](https://camunda.com/blog/category/process-automation-as-a-service/)
* [Documentation Home](https://docs.camunda.io)
* [Issue Tracker](https://github.com/camunda/zeebe/issues)
* [User Forum](https://forum.camunda.io)
* [Slack Channel](https://www.camunda.com/slack)
* [Contribution Guidelines](/CONTRIBUTING.md)

## Recommended Docs Entries for New Users

* [What is Camunda Platform 8?](https://docs.camunda.io/docs/components/concepts/what-is-camunda-platform-8/)
* [Getting Started Tutorial](https://docs.camunda.io/docs/guides/)
* [Technical Concepts](https://docs.camunda.io/docs/components/zeebe/technical-concepts/)
* [BPMN Processes](https://docs.camunda.io/docs/components/modeler/bpmn/bpmn-primer/)
* [Installation and Configuration](https://docs.camunda.io/docs/self-managed/zeebe-deployment/)
* [Java Client](https://docs.camunda.io/docs/apis-clients/java-client/)
* [Go Client](https://docs.camunda.io/docs/apis-clients/go-client/)
* [Spring Integration](https://github.com/camunda-community-hub/spring-zeebe/)

## Contributing

Read the [Contributions Guide](/CONTRIBUTING.md).

## Code of Conduct

This project adheres to the [Camunda Code of Conduct](https://camunda.com/events/code-conduct/).
By participating, you are expected to uphold this code. Please [report](https://camunda.com/events/code-conduct/reporting-violations/)
unacceptable behavior as soon as possible.

## License

Zeebe source files are made available under the [Zeebe Community License
Version 1.1](/licenses/ZEEBE-COMMUNITY-LICENSE-1.1.txt) except for the parts listed
below, which are made available under the [Apache License, Version
2.0](/licenses/APACHE-2.0.txt).  See individual source files for details.

Available under the [Apache License, Version 2.0](/licenses/APACHE-2.0.txt):
- Java Client ([clients/java](/clients/java))
- Go Client ([clients/go](/clients/go))
- Exporter API ([exporter-api](/exporter-api))
- Protocol ([protocol](/protocol))
- Gateway Protocol Implementation ([gateway-protocol-impl](/gateway-protocol-impl))
- BPMN Model API ([bpmn-model](/bpmn-model))

### Clarification on gRPC Code Generation

The Zeebe Gateway Protocol (API) as published in the
[gateway-protocol](/gateway-protocol/src/main/proto/gateway.proto) is licensed
under the [Zeebe Community License 1.1](/licenses/ZEEBE-COMMUNITY-LICENSE-1.1.txt). Using gRPC tooling to generate stubs for
the protocol does not constitute creating a derivative work under the Zeebe Community License 1.1 and no licensing restrictions are imposed on the
resulting stub code by the Zeebe Community License 1.1.
