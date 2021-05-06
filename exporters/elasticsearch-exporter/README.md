# Zeebe Elasticsearch Exporter

The Zeebe Elasticsearch Exporter acts as a bridge between
[Zeebe](https://zeebe.io/) and [Elasticsearch](https://www.elastic.co/products/elasticsearch), by
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

> **Note:** the indexes are created as required, and will not be created twice if they already exist. However,
> once disabled, they will not be deleted, that is up to the administrator. Similarly, data is never deleted by
> the exporter, and must, again, be deleted by the administrator when it is safe to do so.

## Usage

> **Note:** As the exporter is packaged with Zeebe, it is not necessary to specify a `jarPath`.

You can configure the Elasticsearch Exporter with the following arguments:

* `url` (`string`): a valid URLs as comma-separated string (
  e.g. `http://localhost:9200,http://localhost:9201`)

All other options fall under a two categories, both expressed as nested maps: `bulk` and `index`.

### Bulk

To avoid doing too many expensive requests to the Elasticsearch cluster, the exporter performs batch
updates by default. The size of the batch, along with how often it should be flushed (regardless of
size) can be controlled by configuration.

For example:

```yaml
...
exporters:
  elasticsearch:
    args:
      delay: 5
      size: 1000
      memoryLimit: 10485760
```

With the above example, the exporter would aggregate records and flush them to Elasticsearch either:

1. when it has aggregated 1000 records
2. when the batch memory size exceeds 10 MB
3. 5 seconds have elapsed since the last flush (regardless of how many records were aggregated)

More specifically, each option configures the following:

* `delay` (`integer`): a specific delay, in seconds, before we force flush the current batch. This
  ensures that even when we have low traffic of records we still export every once in a while.
* `size` (`integer`): how many records a batch should have before we export.
* `memoryLimit` (`integer`): the size of the bulk, in bytes, before we export.

### Index

In most cases, you will not be interested in exporting every single record produced by a Zeebe
cluster, but rather only a subset of them. This can also be configured to limit the kinds of records
being exported (e.g. only events, no commands), and the value type of these records (e.g. only job
and process values).

For example:

```yaml
...
exporters:
  elasticsearch:
    args:
      index:
        prefix: zeebe-record
        createTemplate: true

        command: false
        event: true
        rejection: false

        deployment: false
        incident: true
        job: false
        jobBatch: false
        message: false
        messageSubscription: false
        processInstance: false
        processMessageSubscription: false
```

The given example would only export incident events, and nothing else.

More specifically, each option configures the following:

* `prefix` (`string`): this prefix will be appended to every index created by the exporter; must not
  contain `_` (underscore).
* `createTemplate` (`boolean`): if true, missing indexes will be created as needed.
* `command` (`boolean`): if true, command records will be exported; if false, ignored.
* `event` (`boolean`): if true, event records will be exported; if false, ignored.
* `rejection` (`boolean`): if true, rejection records will be exported; if false, ignored.
* `deployment` (`boolean`): if true, records related to deployments will be exported; if false,
  ignored.
* `incident` (`boolean`): if true, records related to incidents will be exported; if false, ignored.
* `job` (`boolean`): if true, records related to jobs will be exported; if false, ignored.
* `jobBatch` (`boolean`): if true, records related to job batches will be exported; if false,
  ignored.
* `message` (`boolean`): if true, records related to messages will be exported; if false, ignored.
* `messageSubscription` (`boolean`): if true, records related to message subscriptions will be
  exported; if false, ignored.
* `processInstance` (`boolean`): if true, records related to process instances will be exported; if
  false, ignored.
* `processMessageSubscription` (`boolean`): if true, records related to process message
  subscriptions will be exported; if false, ignored.

Here is a complete, default configuration example:

```yaml
...
exporters:
  elasticsearch:
    # Elasticsearch Exporter ----------
    # An example configuration for the elasticsearch exporter:
    #
    # These setting can also be overridden using the environment variables "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_..."
    #

    className: io.camunda.zeebe.exporter.ElasticsearchExporter

    args:
      # A comma separated list of URLs pointing to the Elasticsearch instances you wish to export to.
      # For example, if you want to connect to multiple nodes for redundancy:
      # url: http://localhost:9200,http://localhost:9201
      url: http://localhost:9200

      bulk:
        delay: 5
        size: 1000
        memoryLimit: 10485760

      authentication:
        username: elastic
        password: changeme

      index:
        prefix: zeebe-record
        createTemplate: true

        command: false
        event: true
        rejection: false

        deployment: false
        process: true
        error: true
        incident: true
        job: true
        jobBatch: false
        message: false
        messageSubscription: false
        variable: true
        variableDocument: true
        processInstance: true
        processInstanceCreation: false
        processMessageSubscription: false
```
