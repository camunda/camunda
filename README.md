# Camunda 8 orchestrates complex business processes that span people, systems, and devices

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.camunda/camunda-zeebe/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.camunda/camunda-zeebe)

Camunda 8 delivers scalable, on-demand process automation as a service. Camunda 8 is combined with powerful execution engines for BPMN processes and DMN decisions, and paired with tools for collaborative modeling, operations, and analytics.

This repository contains the [Orchestration cluster](https://docs.camunda.io/docs/next/components/orchestration-cluster/) components of Camunda 8 and Optimize:

* [Zeebe](https://docs.camunda.io/docs/components/zeebe/zeebe-overview/) - The cloud-native process engine of Camunda 8.
* [Tasklist](https://docs.camunda.io/docs/components/tasklist/introduction-to-tasklist/) - Complete tasks that require human input.
* [Operate](https://docs.camunda.io/docs/components/operate/operate-introduction/) - Manage, monitor, and troubleshoot your processes.
* [Identity](https://docs.camunda.io/docs/next/components/identity/identity-introduction/) - Manage integrated authentication and authorization.
* [Optimize](https://docs.camunda.io/optimize/components/what-is-optimize/) - Improve your processes by identifying constraints in your system.

In addition, the Camunda 8 stack also includes:
* [Console](https://docs.camunda.io/docs/components/console/introduction-to-console/) - Configure and deploy clusters with Console.
* [Web Modeler](https://docs.camunda.io/docs/components/modeler/about-modeler/) - Web Application to model BPMN, DMN, & Forms and deploy or start new instances.
* [Desktop Modeler](https://docs.camunda.io/docs/components/modeler/desktop-modeler/) - Use Desktop Modeler as a desktop application for modeling BPMN, DMN, and Forms with your local process application project.
* [Connectors](https://docs.camunda.io/docs/components/connectors/introduction-to-connectors/) - Integrate with an external system by using a Connector.
* [Management Identity](https://docs.camunda.io/docs/self-managed/identity/what-is-identity/) - Manage authentication, access, and authorization for components outside the Orchestration cluster (Console, Web Modeler, and Optimize)

Using Camunda 8, you can:

* Define processes visually in [BPMN 2.0](https://www.omg.org/spec/BPMN/2.0.2/)
* Choose your programming language
* Deploy with [Docker](https://www.docker.com/) and [Kubernetes](https://kubernetes.io/)
* Build processes that react to messages from [Kafka](https://kafka.apache.org/) and other message queues
* Scale horizontally to handle very high throughput
* Fault tolerance (no relational database required)
* Export process data for monitoring and analysis
* Engage with an active community

[Learn more at camunda.com](https://camunda.com/platform/).

## Status

To learn more about what we're currently working on, check the [GitHub issues](https://github.com/camunda/camunda/issues?q=is%3Aissue+is%3Aopen+sort%3Aupdated-desc) and the [latest commits](https://github.com/camunda/camunda/commits/main).

## Helpful Links

* [Releases](https://github.com/camunda/camunda/releases)
* [Pre-built Docker images](https://hub.docker.com/r/camunda/camunda/tags?page=1&ordering=last_updated)
* [Building Docker images for other platforms](docs/zeebe/building_docker_images.md)
* [Blog](https://camunda.com/blog/category/process-automation-as-a-service/)
* [Documentation Home](https://docs.camunda.io)
* [Issue Tracker](https://github.com/camunda/camunda/issues)
* [User Forum](https://forum.camunda.io)
* [Contribution Guidelines](/CONTRIBUTING.md)

## Documentation

This repository includes comprehensive documentation in the [`docs/`](docs/) directory. The content is served via a Docusaurus site located in [`monorepo-docs-site/`](monorepo-docs-site/).

### Running the Documentation Site

```bash
cd monorepo-docs-site
npm install
npm start
```

The documentation site will be available at `http://localhost:3000/camunda/`.

### Adding Documentation

1. Add new markdown files to the [`docs/`](docs/) directory
2. Configure frontmatter for proper sidebar positioning:
   ```markdown
   ---
   sidebar_position: 1
   title: Your Title
   description: Your description
   ---
   ```

For more details, see the [documentation site README](monorepo-docs-site/README.md).

## Recommended Docs Entries for New Users

* [What is Camunda Platform 8?](https://docs.camunda.io/docs/components/)
* [Getting Started Tutorial](https://docs.camunda.io/docs/guides/)
* [Technical Concepts](https://docs.camunda.io/docs/components/concepts/concepts-overview/)
* [BPMN Processes](https://docs.camunda.io/docs/components/modeler/bpmn/bpmn-primer/)
* [Installation and Configuration](https://docs.camunda.io/docs/self-managed/setup/overview/)
* [Java Client](https://docs.camunda.io/docs/apis-tools/java-client/)
* [Camunda Spring Boot Starter](https://docs.camunda.io/docs/apis-tools/spring-zeebe-sdk/getting-started/)

## Contributing

Read the [Contributions Guide](/CONTRIBUTING.md).

## Code of Conduct

This project adheres to the [Camunda Code of Conduct](https://camunda.com/events/code-conduct/).
By participating, you are expected to uphold this code. Please [report](https://camunda.com/events/code-conduct/reporting-violations/)
unacceptable behavior as soon as possible.

## Release Lifecycle

Please refer to our [Release Policy](https://camunda.com/release-policy/) to learn about our release cadence, maintenance periods, etc.

## License

Zeebe, Operate, and Tasklist source files are made available under the
[Camunda License Version 1.0](/licenses/CAMUNDA-LICENSE-1.0.txt) except for the parts listed
below, which are made available under the [Apache License, Version
2.0](/licenses/APACHE-2.0.txt).  See individual source files for details.

Available under the [Apache License, Version 2.0](/licenses/APACHE-2.0.txt):
- Java Client ([clients/java](/clients/java))
- Camunda Spring Boot Starter ([camunda-spring-boot-starter](/clients/camunda-spring-boot-starter))
- Exporter API ([exporter-api](/zeebe/exporter-api))
- Protocol ([protocol](/zeebe/protocol))
- Gateway Protocol Implementation ([gateway-protocol-impl](/zeebe/gateway-protocol-impl))
- BPMN Model API ([bpmn-model](/zeebe/bpmn-model))

### Clarification on gRPC Code Generation

The Zeebe Gateway Protocol (API) as published in the
[gateway-protocol](/zeebe/gateway-protocol/src/main/proto/gateway.proto) is licensed
under the [Camunda License 1.0](/licenses/CAMUNDA-LICENSE-1.0.txt). Using gRPC tooling to generate stubs for
the protocol does not constitute creating a derivative work under the Camunda License 1.0 and no licensing restrictions are imposed on the
resulting stub code by the Camunda License 1.0.
