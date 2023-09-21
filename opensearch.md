<!-- TOC start (generated with https://github.com/derlin/bitdowntoc) -->

- [Opensearch implementation](#opensearch-implementation)
    * [Structure](#structure)
    * [Setup](#setup)
        + [Start OpenSearch and Zeebe with OpensearchExporter](#start-opensearch-and-zeebe-with-opensearchexporter)
        + [Start Operate with Opensearch implementation](#start-operate-with-opensearch-implementation)
        + [Start Operate with Elasticsearch implementation](#start-operate-with-elasticsearch-implementation)
    * [Status of implementation](#status-of-implementation)
        + [DONE](#done)
        + [TODO](#todo)

<!-- TOC end -->
# Opensearch implementation

Operate uses Elasticsearch to import Zeebe data ([Zeebe Elasticsearch](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/common/src/main/java/io/camunda/operate/property/ZeebeElasticsearchProperties.java#L9-L9)) and stores and searches its own
data also in Elasticsearch ( [Operate Elasticsearch](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/common/src/main/java/io/camunda/operate/property/OperateElasticsearchProperties.java#L9-L9)).

The goal is to do the same in Opensearch. For that Operate needs to implement all Elasticsearch related code also for Opensearch.

## Structure

Operate defines interfaces like

* [Archiver.java](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/archiver/src/main/java/io/camunda/operate/archiver/Archiver.java#L14-L14)
* [IncidentStore.java](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/els-schema/src/main/java/io/camunda/operate/store/IncidentStore.java#L16-L16)
* [DecisionReader.java](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/webapp/src/main/java/io/camunda/operate/webapp/reader/DecisionReader.java#L14-L14)

which all have already the Elasticsearch implementations:

* [ElasticsearchArchiver.java](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/archiver/src/main/java/io/camunda/operate/archiver/elasticsearch/ElasticsearchArchiver.java#L48-L48)
* [ElasticsearchIncidentStore.java](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/els-schema/src/main/java/io/camunda/operate/store/elasticsearch/ElasticsearchIncidentStore.java#L55-L55)
* [DecisionReader.java](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/webapp/src/main/java/io/camunda/operate/webapp/elasticsearch/reader/DecisionReader.java#L48-L48)

Opensearch implementations exists skeletons like:

* [OpensearchIncidentStore.java](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/els-schema/src/main/java/io/camunda/operate/store/opensearch/OpensearchIncidentStore.java#L34-L34)
* [OpensearchDecisionReader.java](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/webapp/src/main/java/io/camunda/operate/webapp/opensearch/reader/OpensearchDecisionReader.java#L20-L20)

which are marked with OpensearchCondition to be instantiated only in case of database = "opensearch".

Most implementations will throw an `UnsupportedOperationException`. Some are already implemented like this:

[OpensearchUserStore.java](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/els-schema/src/main/java/io/camunda/operate/store/opensearch/OpensearchUserStore.java#L40-L40)

## Setup

You need a [Zeebe cli client](https://docs.camunda.io/docs/apis-tools/cli-client/) for interacting with Zeebe.

### Start OpenSearch and Zeebe with OpensearchExporter

1. Start docker containers for Opensearch and Zeebe

`docker-compose up opensearch zeebe-opensearch`

2. Check if Opensearch is reachable at http://localhost:9200:
```json
{
  "name" : "opensearch",
  "cluster_name" : "opensearch-cluster",
  "cluster_uuid" : "jShEGACzQtyq8A9BIvayig",
  "version" : {
    "distribution" : "opensearch",
    "number" : "2.4.1",
    "build_type" : "tar",
    "build_hash" : "f2f809ea280ffba217451da894a5899f1cec02ab",
    "build_date" : "2022-12-12T22:18:22.944497972Z",
    "build_snapshot" : false,
    "lucene_version" : "9.4.2",
    "minimum_wire_compatibility_version" : "7.10.0",
    "minimum_index_compatibility_version" : "7.0.0"
  },
  "tagline" : "The OpenSearch Project: https://opensearch.org/"
}
```
3. Check if Zeebe is reachable

`zbctl --insecure status`:
```
Cluster size: 1
Partitions count: 4
Replication factor: 1
Gateway version: 8.3.0-alpha4
Brokers:
  Broker 0 - 172.21.0.3:26501
    Version: 8.3.0-alpha4
    Partition 1 : Leader, Healthy
    Partition 2 : Leader, Healthy
    Partition 3 : Leader, Healthy
    Partition 4 : Leader, Healthy
```
4. Check if Zeebe can export to Opensearch:

`zbctl --insecure deploy ./data-generator/src/main/resources/develop/calledProcess.bpmn`

should result in something like this:
```json
{
  "key": "2251799813685250",
  "processes": [
    {
      "bpmnProcessId": "called-process",
      "version": 1,
      "processDefinitionKey": "2251799813685249",
      "resourceName": "./data-generator/src/main/resources/develop/calledProcess.bpmn"
    }
  ]
}
```

You should see in Zeebe logs something like this and no error logs
```
2023-08-17 08:52:28.069 [Broker-0] [zb-fs-workers-1] [Exporter-1] INFO
2023-08-17T08:52:28.069802209Z       io.camunda.zeebe.broker.exporter.opensearch - Exporter opened
```

Check in Opensearch whether Zeebe exported data about process deployment:

http://localhost:9200/_cat/indices?v

```
health status index                                                     uuid                   pri rep docs.count docs.deleted store.size pri.store.size
green  open   zeebe-record_process_8.3.0-alpha4_2023-08-17              T9tlKEJ4SFS-qaeYBq3gvw   1   0          4            0     28.1kb         28.1kb
green  open   zeebe-record_command-distribution_8.3.0-alpha4_2023-08-17 bIoF9lkQSvCJnazRDbUeiw   1   0          8            0       13kb           13kb
```

### Start Operate with Opensearch implementation

In order to [switch](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/common/src/main/java/io/camunda/operate/conditions/DatabaseCondition.java#L14-L14) Operate to use [Opensearch implementation](https://github.com/camunda/operate/blob/7ad44931a7d23f5e500dd708d238ce3046e3c71b/common/src/main/java/io/camunda/operate/conditions/OpensearchCondition.java#L9-L9) the configuration for database needs to be set:

`CAMUNDA_OPERATE_DATABASE=opensearch`. If you don't set this `elasticsearch` will be used as database.

For IDE (IntelliJ) you can use this as run configuration:
```
camunda.operate.database=opensearch
camunda.operate.operationExecutor.executorEnabled=false;
logging.io.camunda.operate=INFO
spring.profiles.active=auth,dev,dev-data
```

This starts Operate with test-data generation and underlying opensearch database on http://localhost:8080.

You should see at least:
```
2023-08-17T11:17:03.728+02:00  INFO 12788 --- [           main] i.c.o.WebappModuleConfiguration          : Starting module: webapp
2023-08-17T11:17:03.766+02:00  INFO 12788 --- [           main] i.c.o.z.ZeebeConnector                   : Use plaintext connection to zeebe
2023-08-17T11:17:04.012+02:00  WARN 12788 --- [           main] i.c.o.c.OpensearchConnector              : Username and/or password for are empty. Basic authentication for OpenSearch is not used.
2023-08-17T11:17:04.163+02:00  INFO 12788 --- [           main] i.c.o.c.OpensearchConnector              : OpenSearch cluster health: Green
2023-08-17T11:17:04.187+02:00  WARN 12788 --- [           main] i.c.o.c.OpensearchConnector              : OpenSearch cluster is not accessible
2023-08-17T11:17:04.196+02:00  INFO 12788 --- [           main] i.c.o.c.OpensearchConnector              : OpenSearch cluster health: Green
2023-08-17T11:17:04.244+02:00  INFO 12788 --- [           main] i.c.o.m.ModelMetricProvider              : Register BPMN/DMN model metrics.
2023-08-17T11:17:04.257+02:00  INFO 12788 --- [           main] i.c.o.s.SchemaStartup                    : SchemaStartup started.
2023-08-17T11:17:04.257+02:00  INFO 12788 --- [           main] i.c.o.s.SchemaStartup                    : SchemaStartup: validate schema.
...
2023-08-17T11:17:06.489+02:00  INFO 12788 --- [           main] i.c.o.s.SchemaStartup                    : SchemaStartup finished.
2023-08-17T11:17:06.501+02:00  WARN 12788 --- [           main] i.c.o.c.OpensearchConnector              : Username and/or password for are empty. Basic authentication for OpenSearch is not used.
2023-08-17T11:17:06.506+02:00  INFO 12788 --- [           main] i.c.o.c.OpensearchConnector              : OpenSearch cluster health: Green
2023-08-17T11:17:06.510+02:00  WARN 12788 --- [           main] i.c.o.c.OpensearchConnector              : OpenSearch cluster is not accessible
2023-08-17T11:17:06.541+02:00  INFO 12788 --- [           main] i.c.o.ImportModuleConfiguration          : Starting module: importer
2023-08-17T11:17:06.542+02:00  INFO 12788 --- [           main] i.c.o.ArchiverModuleConfiguration        : Starting module: archiver
```

Operate can connect to Opensearch and create its indices.

But you will see something like this:
```
023-08-17T14:54:00.876+02:00 ERROR 24821 --- [ecords_reader_3] i.c.o.z.o.OpensearchRecordsReader        : null

java.lang.UnsupportedOperationException: null
	at io.camunda.operate.zeebeimport.opensearch.OpensearchRecordsReader.readNextBatchByPositionAndPartition(OpensearchRecordsReader.java:274) ~[classes/:?]
	at io.camunda.operate.zeebeimport.opensearch.OpensearchRecordsReader.readAndScheduleNextBatch(OpensearchRecordsReader.java:148) [classes/:?]
	at io.camunda.operate.zeebeimport.opensearch.OpensearchRecordsReader.readAndScheduleNextBatch(OpensearchRecordsReader.java:134) [classes/:?]
	at io.camunda.operate.zeebeimport.opensearch.OpensearchRecordsReader.run(OpensearchRecordsReader.java:130) [classes/:?]
	at org.springframework.scheduling.support.DelegatingErrorHandlingRunnable.run(DelegatingErrorHandlingRunnable.java:54) [spring-context-6.0.11.jar:6.0.11]```
```

which shows there is a missing implementation.

### Start Operate with Elasticsearch implementation

This should always result without errors and also integration tests should be green. Most of the integration tests are running only with Elasticsearch.

Start in IDE (IntelliJ) just like above without defining the database:

```
logging.level.io.camunda.operate=INFO
server.servlet.context-path=/
spring.profiles.active=auth,dev,dev-data
```

You can also use `make env-up` and `make env-down` for start and stop the whole stack.

## Status of implementation

### DONE

* Connection to Opensearch
* Schema creation (indices)
* Skeletons for Opensearch implementation of interfaces

### TODO

For Opensearch implementation you can use the Elasticsearch implementation of the interface and
rewrite the code in terms of [Opensearch Java Client](https://opensearch.org/docs/latest/clients/java/).

Implementation of interfaces for Opensearch (packages sorted by most important first)
1.  [io.camunda.operate.store.opensearch](https://github.com/camunda/operate/tree/7ad44931a7d23f5e500dd708d238ce3046e3c71b/els-schema/src/main/java/io/camunda/operate/store/opensearch)
2.  [io.camunda.operate.webapp.opensearch](https://github.com/camunda/operate/tree/7ad44931a7d23f5e500dd708d238ce3046e3c71b/webapp/src/main/java/io/camunda/operate/webapp/opensearch)
3.  [io.camunda.operate.archiver.opensearch](https://github.com/camunda/operate/tree/7ad44931a7d23f5e500dd708d238ce3046e3c71b/archiver/src/main/java/io/camunda/operate/archiver/opensearch)
4.  [io.camunda.operate.schema.migration.opensearch](https://github.com/camunda/operate/tree/7ad44931a7d23f5e500dd708d238ce3046e3c71b/els-schema/src/main/java/io/camunda/operate/schema/opensearch)
5.  [Public API](https://github.com/camunda/operate/tree/7ad44931a7d23f5e500dd708d238ce3046e3c71b/webapp/src/main/java/io/camunda/operate/webapp/api) even needs abstraction layer.
