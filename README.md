# Zeebe.io - Microservice Orchestration Engine


[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.zeebe/zeebe-distribution/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.zeebe/zeebe-distribution)

Zeebe scales orchestration of workers and microservices using visual workflows. Zeebe is horizontally scalable and fault tolerant so that you can reliably process all your transactions as they happen.

**Features**

* Visual Workflows
* Audit Logs and History
* Horizontal Scalability
* Durability & Fault Tolerance
* Fully Message-Driven
* Easy to operate
* Language agnostic

[Learn more](https://docs.zeebe.io/basics/README.html)

## DISCLAIMER

Zeebe is currently a tech preview and not meant for production use - See [Roadmap](https://zeebe.io/roadmap).

## Links

* [Web Site](https://zeebe.io)
* [Documentation](https://docs.zeebe.io)
* [Issue Tracker](https://github.com/zeebe-io/zeebe/issues)
* [Slack Channel](https://zeebe-slack-invite.herokuapp.com/)
* [User Forum](https://forum.zeebe.io)
* [Contribution Guidelines](/CONTRIBUTING.md)

## Documentation

* [Introduction](https://docs.zeebe.io/introduction/README.html)
* [Basics](https://docs.zeebe.io/basics/README.html)
* [Configuration](https://docs.zeebe.io/operations/the-zeebecfgtoml-file.html)
* [Java Client](https://docs.zeebe.io/java-client/README.html)
* [BPMN Workflows](https://docs.zeebe.io/bpmn-workflows/README.html)

## Contributing

Read the [Contributions Guide](/CONTRIBUTING.md)

## Code of Conduct

This project adheres to the Contributor Covenant [Code of
Conduct](/CODE_OF_CONDUCT.md). By participating, you are expected to uphold
this code. Please report unacceptable behavior to
code-of-conduct@zeebe.io.

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
