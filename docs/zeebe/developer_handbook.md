# Developer Handbook

This document contains instructions for developers who want to contribute to this project.

## How to extend the Gateway Protocol?

* The gateway protocol is based on GRPC
* The single source of truth is the [`gateway.proto`](../../zeebe/gateway-protocol/src/main/proto/gateway.proto) [Protocol Buffers](https://developers.google.com/protocol-buffers) file
* Source code is generated based on the information in that file
* Make your changes in that file
* Add comments to new fields/messages you added
* Java sources for the protobuf classes will be generated automatically with each build
* Go sources are generated on demand [Go Code Generation](../../zeebe/gateway-protocol-impl/README.md#go-code-generation).
* Remember to also update the GRPC API documentation https://docs.camunda.io/docs/apis-clients/grpc/

## How to create a new record?

Generally, you'll need to do the following things:
1. [Expand our `protocol` with a new `RecordValue`](#expanding-our-protocol-with-a-new-recordvalue).
2. [Implement this `RecordValue` in the `protocol-impl` module](#implement-a-new-recordvalue-in-protocol-impl).
3. Support this `RecordValue` in the [Elasticsearch exporter](#support-a-recordvalue-in-the-elasticsearch-exporter) and [Opensearch exporter](#support-a-recordvalue-in-the-opensearch-exporter).
4. [Extend the official exporter documentation](#extend-official-documentation).
5. [Support the new `ValueType` in Zeebe Process Test (ZPT)](#extend-zeebe-process-test).
6. [Ensure that the new `ValueType` is processed](#add-valuetype-to-supported-types).
7. Add support for it to the [CompactRecordLogger](../../zeebe/test-util/src/main/java/io/camunda/zeebe/test/util/record/CompactRecordLogger.java).

### Expanding our protocol with a new RecordValue

The protocol consists of Java code and [SBE message definitions](../protocol/src/main/resources/). When compiled, Java code is generated that allows us to serialize our records into the SBE format and deserialize them back into Java.

Please have a look at [Message Versioning](https://github.com/real-logic/simple-binary-encoding/wiki/Message-Versioning) to learn about extending SBE messages.

1. Add a new `<validValue>` to the `ValueType` enum in [`protocol.xml`](../../zeebe/protocol/src/main/resources/protocol.xml).

2. Create an enum implementing [`Intent`](../../zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/Intent.java) to reflect the possible commands and events:

- It should also be added to the `INTENT_CLASSES` defined in Intent
- Make sure to add the new intent as a case to `Intent.fromProtocolValue`

3. Create an interface for the record value itself.

- It should extend the [`RecordValue`](../../zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/RecordValue.java) interface
- Add methods for each of the properties that you want to expose
- If you need to create additional data types, then you can create new interfaces to represent these
- Annotate the interfaces with:
  * `@Value.Immutable`
  * `@ImmutableProtocol(builder = Immutable...RecordValue.Builder.class)`, replacing `...` with the name of the interface

    > Note, that the Immutable class is only available after building (i.e. it is generated from these annotations), so you may see a compile error when you add this annotation. To resolve the compile error, just build the protocol.

4. Build the protocol: `mvn clean install -pl :zeebe-protocol` to generate the Immutable classes and to generate the new `ValueType` enum value.

5. Add a mapping to [`ValueTypeMapping`](../../zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/ValueTypeMapping.java) connecting the `ValueType`, `RecordValue` and `Intent` together.

### Implement a new RecordValue in protocol-impl

1. Implement your new `RecordValue` interface in [protocol-impl](../protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/).

- Make sure to add annotations for properties (or getter methods) that shouldn't be serialized to JSON.

2. Add new cases to [JsonSerializableToJsonTest](../../zeebe/protocol-impl/src/test/java/io/camunda/zeebe/protocol/impl/JsonSerializableToJsonTest.java):

- one case that provides a value for each property (as far nested as possible)
- one case that has as few properties as possible (i.e. an empty record)

3. Add the new `Record` to the broker's [CommandApiRequestReader](../../zeebe/broker/src/main/java/io/camunda/zeebe/broker/transport/commandapi/CommandApiRequestReader.java)'s `RECORDS_BY_TYPE` mapping.

### Support a RecordValue in the Elasticsearch exporter

You'll always need to add support for new records in the ES exporter. Even if you don't yet want to export a new record, our tests will fail if you don't provide this support. Note that in step 3 below, you can choose whether or not the record is exported to ES by default.

1. Add a record template to the elastic search exporter's [resources](../exporters/elasticsearch-exporter/src/main/resources/).

- Tip: start by copying an existing template and change the relevant properties.

2. Add a call to `createValueIndexTemplate` for the `ValueType` in [ElasticsearchExporter](../../zeebe/exporters/elasticsearch-exporter/src/main/java/io/camunda/zeebe/exporter/ElasticsearchExporter.java).
3. Allow the record to be filtered through the [configuration](../../zeebe/exporters/elasticsearch-exporter/src/main/java/io/camunda/zeebe/exporter/ElasticsearchExporterConfiguration.java).
4. Document this new filter option in the dist folder's [broker config templates](../dist/src/main/config/).
5. Add a mapping for the ValueType to the [TestSupport](../../zeebe/exporters/elasticsearch-exporter/src/test/java/io/camunda/zeebe/exporter/TestSupport.java).

### Support a RecordValue in the Opensearch exporter

You'll always need to add support for new records in the OS exporter. Even if you don't yet want to export a new record,
our tests will fail if you don't provide this support. Note that in step 3 below, you can choose whether or not the record is exported to OS by default.

1. Add a record template to the exporter's [resources](../exporters/opensearch-exporter/src/main/resources/).

- Tip: start by copying an existing template and change the relevant properties.

2. Add a call to `createValueIndexTemplate` for the `ValueType` in [OpensearchExporter](../../zeebe/exporters/opensearch-exporter/src/main/java/io/camunda/zeebe/exporter/opensearch/OpensearchExporter.java).
3. Allow the record to be filtered through the [configuration](../../zeebe/exporters/opensearch-exporter/src/main/java/io/camunda/zeebe/exporter/opensearch/OpensearchExporterConfiguration.java).
4. Document this new filter option in the dist folder's [broker config templates](../dist/src/main/config/).
5. Add a mapping for the ValueType to the [TestSupport](../../zeebe/exporters/opensearch-exporter/src/test/java/io/camunda/zeebe/exporter/opensearch/TestSupport.java).

### Extend official documentation

Our Exporter configurations are documented in the [official docs](https://github.com/camunda/camunda-platform-docs).
In the previous steps we've extended the configuration of the Elasticsearch and OpenSearch exporters.
These configurations options need to be added to the official documentation.

### Extend Zeebe Process Test

The [Zeebe Process Test (ZPT)](https://github.com/camunda/camunda-process-test/) library allows you to unit test your Camunda Platform 8 BPMN processes.
The [RecordStreamLogger](https://github.com/camunda/camunda-process-test/blob/main/filters/src/main/java/io/camunda/zeebe/process/test/filters/logger/RecordStreamLogger.java) class
defines how the supported `ValueType`s' records are logged in tests.

In the previous steps, we've added a new value type. This type needs to be added here to the `valueTypeLoggers`.

### Add ValueType to supported types

The engine defines a [range of supported value types](https://github.com/camunda/camunda/blob/12b1bc659433e79cc4d84194a826ee3af924a308/zeebe/engine/src/main/java/io/camunda/zeebe/engine/Engine.java#L51-L52) it processes,
in command processing and in replay mode.
In the previous steps, we've added a new value type. This type needs to be added here to ensure that commands of this type will be processed and events will be replayed.
Otherwise, brokers will not process related commands and will miss the entity updates that happened in events for the new value type, which leads to data loss.

## How to extend an existing record?

Use case: adding a new property to an existing record.

Extending an existing record is a special case
of [creating a new record](#how-to-create-a-new-record) but with fewer steps.

You'll need to do 4 things:

1. Extend the existing `RecordValue` interface in the `protocol` module.

- In general, the interface exposes all properties of the record.
- As a result, they are available in our tests and other modules.

2. Adjust the implementation of this `RecordValue` in the `protocol-impl` module.

- Including
  the [JsonSerializableToJsonTest](../../zeebe/protocol-impl/src/test/java/io/camunda/zeebe/protocol/impl/JsonSerializableToJsonTest.java).

3. Adjust the template of this `RecordValue` in the Elasticsearch exporter.

- We need to add all properties of the record, even if they are not consumed yet. The
  template is defined `strict`.
- We don't need to adjust the test cases because they are generated based on the `RecordValue`
  interface. If a property is not in the interface then it is not covered by the tests.

4. (Optionally) Adjust
   the [CompactRecordLogger](../../zeebe/test-util/src/main/java/io/camunda/zeebe/test/util/record/CompactRecordLogger.java)
   of this `RecordValue`.

## How to do inter-partition communication?

Generally, each [partition](https://docs.camunda.io/docs/next/components/zeebe/technical-concepts/partitions/) is isolated from the others. The gateway chooses the partition for each user command and sends the command to that partition's leader. This distributes the load over the partitions. When the engine processes commands, the follow-up records are written on the same partition.

In some cases, a partition needs to communicate with other partitions:
- Deployment distribution: Each partition must know the related process and decision models. The deployment distribution ensures each partition knows the process and decision models.
- Message subscription and correlation: messages are published on a specific partition and correlated to process instances on other partitions to keep messaging scalable. This requires the engine to open subscriptions on the relevant partitions.

### How to send a command to another partition?

The engine can send a command to another partition using `InterPartitionCommandSender`. Note that this may fail because network communication is not reliable. So for all inter-partition communication, you must use some retry mechanism.

For example, the `DeploymentRedistributor` resends deployment distribution commands to other partitions.

> **Note**
> There is no response for inter-partition communication. It is a case of "fire and forget".

### How to process a command received from another partition?

As we've seen, the sending partition may send a command many times to another partition. So, the engine must be able to deal with these redundant commands (i.e. commands that are sent many times). To be specific, the engine must process commands received from another partition idempotently.

> **Note**
> By idempotent command processing, we mean that the engine arrives at the same state when it processes the redundant command, as when it processed the original command.

Generally, there are two ways we could idempotently process inter-partition commands:
1. (**our preferred way**) reject redundant commands by writing a command rejection to the log, and don't change the state at all.
2. (_not used by us_) produce the same events that, when applied, produce the same state changes, resulting in the same state.

> **Note**
> We aim to have a consistent approach and have decided to use option 1, for the following reasons:
> - the command rejection is easier to understand when reading the log, compared to seeing the same event twice.
> - the command rejection describes a reason to further clarify what happened.
> - idempotency in event appliers can be easily achieved in some cases (e.g. using upsert over insert to change the state), but this could hide actual consistency problems.

## How to run docker image of a specific platform locally

You can run a docker image of a different than your native platform locally by using [binfmt](https://github.com/tonistiigi/binfmt), a cross-platform emulator collection for docker. You can either install all emulators via:

```
docker run --privileged --rm tonistiigi/binfmt --install all
```

or just specific ones, see the [install section for binfmt](https://github.com/tonistiigi/binfmt#installing-emulators).

Once binfmt is setup you can explicitly run the zeebe image for a specific platform by specifying it to the run command:

```
docker run --platform=linux/arm64 camunda/zeebe:SNAPSHOT
```

or using the equivalent [platform property of docker-compose](https://docs.docker.com/compose/compose-file/#platform).

## How to create a new REST endpoint

Follow the [REST endpoint guide](../rest-controller.md). It will walk you through the required steps to consider.
