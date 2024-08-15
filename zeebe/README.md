# Zeebe

Zeebe is the process automation engine powering Camunda 8. While written in Java, you do not need to be a Java developer to use Zeebe. To learn more about Zeebe, [visit the Camunda docs.](https://docs.camunda.io/docs/components/zeebe/zeebe-overview/)

## Modules

This is a small overview of the contents of the different modules:
- `util` contains custom implementations of building blocks like an actor scheduler, buffer allocations, and metrics. Its parts are used in most of the other modules
- `protocol` contains the SBE definition of the main message protocol
- `bpmn-model` is a Java API for BPMN process definitions used for parsing etc.
- `msgpack-*` is a custom msgpack implementation with extensions to evaluate json-path expressions on msgpack objects
- `dispatcher` is a custom implementation of message passing between threads
- `service-container` is a custom implementation to manage dependencies between different services
- `logstreams` is an implementation of an append-only log backed by the filesystem
- `transport` is our abstraction over network transports
- `gateway` is the implementation of the gRPC gateway, using our SBE-based protocol to communicate with brokers
- `gateway-protocol` contains the gRPC definitions for the Zeebe client-to-gateway protocol
- `zb-db` is our RocksDB wrapper for state management
- `engine`  is the implementation of the event stream processor
- `broker` contains the Zeebe broker which is the server side of Zeebe
- `client-java` contains the Java Zeebe client
- `atomix` contains transport, membership, and consensus algorithms
- `benchmark` contains utilities the team uses to run load tests
- `exporters/elasticsearch-exporter` contains the official Elasticsearch exporter for Zeebe
- `journal` contains the append-only log used by the consensus algorithm
- `snapshots` abstracts how state snapshots (i.e. `zb-db`) are handled
