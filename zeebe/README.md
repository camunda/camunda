# Zeebe

Zeebe is the process automation engine powering Camunda 8. While written in Java, you do not need to be a Java developer to use Zeebe. To learn more about Zeebe, [visit the Camunda docs.](https://docs.camunda.io/docs/components/zeebe/zeebe-overview/)

## Modules

This is a small overview of the contents of the different modules:
* `auth` authentication and authorization components shared by broker and gateway
* `atomix` contains transport, membership, and consensus algorithms
* `backup` abstracts how primary storage backups are handled in Zeebe
* `backup-stores` implementations for different backup stores
* `backup-stores/azure` Microsoft Azure Blob Storage backup store implementation
* `backup-stores/common` shared abstractions for backup store implementations
* `backup-stores/filesystem` filesystem-based backup store
* `backup-stores/gcs` Google Cloud Storage backup store implementation
* `backup-stores/s3` Amazon S3 backup store implementation
* `backup-stores/testkit` testing utilities/fakes for backup stores
* `bpmn-model` is a Java API for BPMN process definitions used for parsing etc.
* `broker` contains the Zeebe broker which is the server side of Zeebe
* `broker-client` client for internal communication between cluster components
* `client-java` contains the Java Zeebe client
* `dmn` implementation of the DMN standard in the engine
* `dynamic-config` dynamic configuration management for brokers
* `engine` is the implementation of the event stream processor
* `exporter-api` exporter SPI for implementing exporters
* `exporters/camunda-exporter` Camunda Platform exporter implementation
* `exporters/elasticsearch-exporter` contains the official Elasticsearch exporter for Zeebe
* `exporters/opensearch-exporter` OpenSearch exporter implementation
* `exporters/rdbms-exporter` RDBMS exporter implementation
* `exporters/app-integrations-exporter` App Integrations exporter implementation
* `exporter-common` common utilities for exporter implementations
* `exporter-test` thread-safe exporter test controller
* `expression-language` engine core expression evaluation
* `feel` engine integration with FEEL expression language
* `feel-tagged-parameters` tagged parameter support FEEL extension
* `gateway` is the implementation of the gRPC gateway, using our SBE-based protocol to communicate with brokers
* `gateway-grpc` gateway gRPC server implementation
* `gateway-protocol` contains the gRPC definitions for the Zeebe client-to-gateway protocol
* `gateway-protocol-impl` gRPC protocol service definition
* `gateway-rest` gateway REST API implementation
* `journal` contains the append-only log used by the consensus algorithm
* `logstreams` is an implementation of an append-only log backed by the filesystem
* `msgpack-core` core Message Pack encoding/decoding implementation
* `msgpack-value` Message Pack property value representation
* `protocol` contains the SBE definition of the main message protocol
* `protocol-asserts` protocol generated _assertj_ assertion utilities
* `protocol-impl` protocol record definitions
* `protocol-jackson` Jackson (JSON) mapping support for Zeebe protocol objects
* `protocol-test-util` test utilities for working with protocol record and SBE
* `restore` implementation of restoring primary storage state from backups
* `scheduler` actor scheduler implementation
* `service-container` is a custom implementation to manage dependencies between different services
* `snapshots` abstracts how state snapshots (i.e. `zb-db`) are handled
* `stream-platform` composable stream processing platform primitives used by the engine
* `test-util` shared testing utilities
* `transport` is our abstraction over network transports
* `util` contains custom implementations of building blocks like an actor scheduler, buffer allocations, and metrics. Its parts are used in most of the other modules
* `zb-db` is our RocksDB wrapper for state management
* `qa` engine integration test suite
