# Zeebe.io - Workflow Engine for Microservices Orchestration


[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.zeebe/zeebe-distribution/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.zeebe/zeebe-distribution)

Zeebe provides visibility into and control over business processes that span multiple microservices.

**Why Zeebe?**

* Define workflows visually in [BPMN 2.0](https://www.omg.org/spec/BPMN/2.0.2/)
* Choose your programming language
* Deploy with [Docker](https://www.docker.com/) and [Kubernetes](https://kubernetes.io/)
* Build workflows that react to messages from [Kafka](https://kafka.apache.org/) and other message queues
* Scale horizontally to handle very high throughput
* Fault tolerance (no relational database required)
* Export workflow data for monitoring and analysis
* Engage with an active community

[Learn more at zeebe.io](https://zeebe.io)

## Status

Starting with Zeebe 0.20.0, the "developer preview" label was removed from Zeebe and the first production-ready version was released  .

To learn more about what we're currently working on, please visit the [roadmap](https://zeebe.io/roadmap).

## Helpful Links

* [Blog](https://zeebe.io/blog)
* [Documentation Home](https://docs.zeebe.io)
* [Issue Tracker](https://github.com/zeebe-io/zeebe/issues)
* [User Forum](https://forum.zeebe.io)
* [Slack Channel](https://zeebe-slack-invite.herokuapp.com/)
* [Contribution Guidelines](/CONTRIBUTING.md)

## Recommended Docs Entries for New Users

* [What is Zeebe?](https://docs.zeebe.io/introduction/what-is-zeebe.html)
* [Core Concepts](https://docs.zeebe.io/basics/index.html)
* [Getting Started Tutorial](https://docs.zeebe.io/getting-started/index.html)
* [BPMN Workflows](https://docs.zeebe.io/bpmn-workflows/index.html)
* [Configuration](https://docs.zeebe.io/operations/configuration.html)
* [Java Client](https://docs.zeebe.io/java-client/index.html)
* [Go Client](https://docs.zeebe.io/go-client/index.html)


## Contributing

Read the [Contributions Guide](/CONTRIBUTING.md).

## Code of Conduct

This project adheres to the [Camunda Code of Conduct](https://camunda.com/events/code-conduct/).
By participating, you are expected to uphold this code. Please [report](https://camunda.com/events/code-conduct/reporting-violations/)
unacceptable behavior as soon as possible.

## License

Zeebe source files are made available under the [Zeebe Community License
Version 1.0](/licenses/ZEEBE-COMMUNITY-LICENSE-1.0.txt) except for the parts listed
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
under the Zeebe Community License 1.0. Using gRPC tooling to generate stubs for
the protocol does not constitute creating a derivative work under the Zeebe
Community License 1.0 and no licensing restrictions are imposed on the
resulting stub code by the Zeebe Community License 1.0.
