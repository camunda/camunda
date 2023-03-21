# Zeebe Opensearch Exporter

The Zeebe Opensearch Exporter acts as a bridge between
[Zeebe](https://camunda.com/platform/zeebe/) and [Opensearch](https://opensearch.org), by
exporting records written to Zeebe streams as documents into several indices.

## Concept

The exporter operates on the idea that it should perform as little as possible on the Zeebe side of
things. In other words, you can think of the indexes into which the records are exported as a
staging data warehouse: any enrichment or transformation on the exported data should be performed by
your own ETL jobs.

To simplify things, when configured to do so, the exporter will automatically create an index per
record value type (see the value type in the Zeebe protocol). Each of these indexes has a
corresponding pre-defined mapping to facilitate data ingestion for your own ETL jobs. You can find
those as templates in this module's resources folder.

> **Note:** the indexes are created as required, and will not be created twice if they already
> exist. However,
> once disabled, they will not be deleted, that is up to the administrator. Similarly, data is never
> deleted by
> the exporter, and must, again, be deleted by the administrator when it is safe to do so.

## Configuration

> **Note:** As the exporter is packaged with Zeebe, it is not necessary to specify a `jarPath`.

The exported can be enabled by configuring it with the classpath in the Broker settings.
For example:

```yaml
exporters:
  opensearch:
    className: io.camunda.zeebe.exporter.opensearch.OpensearchExporter
    args:
    # Please refer to the table below for the available args options
```

The exporter can be configured by providing `args`. The table below explains all the different
options, and the default values for these options.

|      Option      |                                       Description                                       |         Default         |
|------------------|-----------------------------------------------------------------------------------------|-------------------------|
| url              | Valid URLs as comma-separated string                                                    | `http://localhost:9200` |
| requestTimeoutMs | Request timeout (in ms) for the Opensearch client                                       | `30000`                 |
| index            | Refer to [Index](#Index) for the index configuration options                            |                         |
| bulk             | Refer to [Bulk](#Bulk) for the bulk configuration options                               |                         |
| authentication   | Refer to [Authentication](#Authentication) for the authentication configuration options |                         |
| aws              | Refer to [AWS](#AWS) for the aws configuration options                                  |                         |

### Index

In most cases, you will not be interested in exporting every single record produced by a Zeebe
cluster, but rather only a subset of them. This can also be configured to limit the kinds of records
being exported (e.g. only events, no commands), and the value type of these records (e.g. only job
and process values).

|            Option             |                                               Description                                               |   Default    |
|-------------------------------|---------------------------------------------------------------------------------------------------------|--------------|
| prefix                        | This prefix will be appended to every index created by the exporter; must not contain `_` (underscore). | zeebe-record |
| createTemplate                | If `true` missing indexes will be created automatically.                                                | `true`       |
| command                       | If `true` command records will be exported                                                              | `false`      |
| event                         | If `true` event records will be exported                                                                | `true`       |
| rejection                     | If `true` rejection records will be exported                                                            | `false`      |
| checkpoint                    | If `true` records related to checkpoints will be exported                                               | `false`      |
| commandDistribution           | If `true` records related to command distributions will be exported                                     | `true`       |
| decision                      | If `true` records related to decisions will be exported                                                 | `true`       |
| decisionEvaluation            | If `true` records related to decision evaluations will be exported                                      | `true`       |
| decisionRequirements          | If `true` records related to decisionRequirements will be exported                                      | `true`       |
| deployment                    | If `true` records related to deployments will be exported                                               | `true`       |
| deploymentDistribution        | If `true` records related to deployment distributions will be exported                                  | `true`       |
| error                         | If `true` records related to errors will be exported                                                    | `true`       |
| escalation                    | If `true` records related to escalations will be exported                                               | `true`       |
| incident                      | If `true` records related to incidents will be exported                                                 | `true`       |
| job                           | If `true` records related to jobs will be exported                                                      | `true`       |
| jobBatch                      | If `true` records related to job batches will be exported                                               | `false`      |
| message                       | If `true` records related to messages will be exported                                                  | `true`       |
| messageSubscription           | If `true` records related to message subscriptions will be exported                                     | `true`       |
| messageStartEventSubscription | If `true` records related to message start event subscriptions will be exported                         | `true`       |
| process                       | If `true` records related to processes will be exported                                                 | `true`       |
| processEvent                  | If `true` records related to process events will be exported                                            | `false`      |
| processInstance               | If `true` records related to process instances will be exported                                         | `true`       |
| processInstanceCreation       | If `true` records related to process instance creations will be exported                                | `true`       |
| processInstanceModification   | If `true` records related to process instance modifications will be exported                            | `true`       |
| processMessageSubscription    | If `true` records related to process message subscriptions will be exported                             | `true`       |
| resourceDeletion              | If `true` records related to resource deletions will be exported                                        | `true`       |
| signal                        | If `true` records related to signals will be exported                                                   | `true`       |
| signalSubscription            | If `true` records related to signal subscriptions will be exported                                      | `true`       |
| timer                         | If `true` records related to timers will be exported                                                    | `true`       |
| variable                      | If `true` records related to variables will be exported                                                 | `true`       |
| variableDocument              | If `true` records related to variable documents will be exported                                        | `true`       |

### Bulk

To avoid doing too many expensive requests to the Opensearch cluster, the exporter performs batch
updates by default. The size of the batch, along with how often it should be flushed (regardless of
size) can be controlled by configuration.

|   Option    |                                                                         Description                                                                          |      Default       |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------|
| delay       | Delay, in seconds, before we force flush the current batch. This ensures that even when we have low traffic of records we still export every once in a while | `5`                |
| size        | The amount of records a batch should have before we flush the batch                                                                                          | `1000`             |
| memoryLimit | The size of the batch, in bytes, before we flush the batch                                                                                                   | `10485760` (10 MB) |

With the default configuration, the exporter would aggregate records and flush them to Opensearch
either:

1. when it has aggregated 1000 records
2. when the batch memory size exceeds 10 MB
3. 5 seconds have elapsed since the last flush (regardless of how many records were aggregated)

### Authentication

Providing these authentication options will enable Basic Authentication on the Exporter.

|  Option  |          Description          | Default |
|----------|-------------------------------|---------|
| username | Username used to authenticate | N/A     |
| password | Password used to authenticate | N/A     |

### AWS

When running Opensearch in AWS you may require requests to be signed. By enabling AWS in the
configurations a request interceptor will be added to the exporter. This interceptor will take care
of signing the requests.

Signing requests requires credentials. These credentials are not directly configurable in the
exporter. Instead, they are resolved by following the
[Default Credential Provider Chain](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html)

|   Option    |                                       Description                                        |                      Default                       |
|-------------|------------------------------------------------------------------------------------------|----------------------------------------------------|
| enabled     | Enables AWS request signing                                                              | `false`                                            |
| serviceName | AWS's name of the service to where requests are made. For Opensearch this should be `es` | `es`                                               |
| region      | The region this exporter is running in                                                   | The value of the `AWS_REGION` environment variable |

## Example

Here is an example configuration of the Exporter.

```yaml
...
exporters:
  opensearch:
    # Opensearch Exporter ----------
    # An example configuration for the opensearch exporter:
    #
    # These setting can also be overridden using the environment variables "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_..."

    className: io.camunda.zeebe.exporter.opensearch.OpensearchExporter

    args:
      # A comma separated list of URLs pointing to the Opensearch instances you wish to export to.
      # For example, if you want to connect to multiple nodes for redundancy:
      # url: http://localhost:9200,http://localhost:9201
      url: http://localhost:9200

      bulk:
        delay: 5
        size: 1000
        memoryLimit: 10485760

      authentication:
        username: opensearch
        password: changeme

      aws:
        enabled: true
        serviceName: es
        region: eu-west-1

      index:
        prefix: zeebe-record
        createTemplate: true

        command: false
        event: true
        rejection: false

        commandDistribution: true
        decisionRequirements: true
        decision: true
        decisionEvaluation: true
        deployment: true
        deploymentDistribution: true
        error: true
        escalation: true
        incident: true
        job: true
        jobBatch: false
        message: true
        messageStartSubscription: true
        messageSubscription: true
        process: true
        processEvent: false
        processInstance: true
        processInstanceCreation: true
        processInstanceModification: true
        processMessageSubscription: true
        resourceDeletion: true
        signal: true
        signalSubscription: true
        timer: true
        variable: true
        variableDocument: true
```

