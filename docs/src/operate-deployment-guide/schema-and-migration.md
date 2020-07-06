# Schema & Migration

Operate stores data in Elasticsearch. On first start Operate will create all required indices and templates.

* [Schema](#schema)
* [Data Migration](#data-migration)
* [How to migrate](#how-to-migrate)
* [Example for migrate in Kubernetes](#example-for-migrate-in-kubernetes)

## Schema

Operate uses several Elasticsearch indices that are mostly created by using templates.

Index names follow the defined pattern:
```
operate-{datatype}-{schemaversion}_[{date}]

```
, where `datatype` defines which data is stored in the index, e.g. `user`, `variable` etc.,
`schemaversion` represents version of Operate,
`date` represents finished date of archived data (see [Data retention](data-retention.md)).

Knowing index name pattern, it is possible to customize index settings by creating Elasticsearch templates ([Example of an index template](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/indices-templates.html))
E.g. to define desired number of shards and replicas, you can define following template:
```
PUT _template/template_operate
{
  "index_patterns": ["operate-*"],
  "settings": {
    "number_of_shards": 5,
    "number_of_replicas": 2
  }
}
```

> **Note:** In order for these settings to work, template must be created before Operate first run.

## Data migration

Version of Operate is reflected in Elasticsearch object names, e.g. `operate-user-1.3.0_` index contains user data for Operate v. 1.3.0. When upgrading from one
version of Operate to another, migration of data must be performed. Operate distribution provides an application to perform data migration from previous versions.


### Concept

The migration uses Elasticsearch [processors](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/ingest-processors.html) and [pipelines](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/pipeline.html) to reindex the data.

Each version of Operate delivers set of migration steps needed to be applied for corresponding version of Operate.
When upgrading from one version to another necessary migration steps constitute the so-called migration plan.
All known migration steps (both applied and not) are persisted in dedicated Elasticsearch index: `operate-migration-steps-repository`.


### How to migrate

#### Migrate by using standalone application

Make sure that Elasticsearch which contains the Operate data is running. The migration script will connect to specified connection in Operate
configuration (```<operate_home>/config/application.yml```).

Execute ```<operate_home>/bin/migrate``` (or ```<operate_home>/bin/migrate.bat``` for Windows).

What is expected to happen:
* Elasticsearch schema of new version is created
* previous version is detected
* migration plan is built and executed reindexing data for each index
* old indices are deleted

All known migration steps with metadata will be stored in `operate-migration-steps-repository` index.

> **Note:** The old indices will be deleted ONLY after successful migration. That might require
>more disk space during migration process.

> **Important!** You should take care of data backup before performing migration.

#### Migrate by using built-in automatic upgrade

When running newer version of Operate against older schema, it will perform data migration on startup.
The migration will happen when exactly ONE previous schema version was detected.

#### Further notes

* If migration fails, it is OK to retry it. All applied steps are stored and only those steps will be applied that hasn't been executed yet.
* Operate should not be running, while migration is happening
* In case version upgrade is performed in the cluster with several Operate nodes, only one node ([Webapp module](importer-and-archiver.md)) must execute data migration, the others must be stopped
and started only after migration is fully finished

#### Configure migration

Automatic migration is enabled by default.
It can be disabled by setting the configuration key:

`camunda.operate.migration.migrationEnabled = false`

You can specify previous ("source") version with the configuration key:

`camunda.operate.migration.sourceVersion=0.23.0`

If no *sourceVersion* is defined Operate tries to detect it from Elasticsearch indices.


#### Example for migrate in Kubernetes

To ensure that the migration will be executed *before* Operate will be started you can use
the [init container](https://kubernetes.io/docs/concepts/workloads/pods/init-containers/) feature of Kubernetes. It makes sure that the 'main' container will only be started
if the initContainer was successfully executed.
The following snippet of a pod description for Kubernetes shows the usage of `migrate` script as initContainer.

```
...
  labels:
    app: operate
spec:
   initContainers:
     - name: migration
       image: camunda/operate:0.24.0
       command: ['/bin/sh','/usr/local/operate/bin/migrate']
   containers:
     - name: operate
       image: camunda/operate:0.24.0
       env:
...
```

