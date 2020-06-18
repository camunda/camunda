# Schema & Migration

Operate stores data in Elasticsearch. On first start Operate will create all required indices and templates.
All information describing Elasticsearch schema and required for migration is provided in `schema` folder of Operate distribution. 

* [Schema](#schema)
* [Migration](#migration)
* [How to migrate](#how-to-migrate)
* [Example for migrate in Kubernetes](#example-for-migrate-in-kubernetes)

## Schema

Operate uses several Elasticsearch indices that are mostly created by using templates.  
For each index exists a JSON file that describes how the index is structured and how it is configured. These JSON files
follow the Elasticsearch REST API format and can be found in `schema/create` folder of distribution. 

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

## Migration

Version of Operate is reflected in Elasticsearch object names, e.g. `operate-user-1.3.0_` index contains user data for Operate v. 1.3.0. When upgrading from one 
version of Operate to another, migration of data must be performed. Operate distribution provides an application to perform data migration from previous versions. 


### Concept
The migration uses Elasticsearch [processors](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/ingest-processors.html) and [pipelines](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/pipeline.html) to reindex the data. 

Migration execute plans for each index. A plan consists of a sequence of steps.
A step describes one change in an index for a particular version. 
The step has an index name, a version where the change happened, an order number to determine the position in the plan and the content which describes what will be changed.
To track the application of a step and also for protocol usage the step stores additionally an applied marker and created and applied dates.
The description field is for documentation purposes.

All available steps will be stored in its own index in Elasticsearch ( `operate-migration-steps-repositoy`) Each change in schema will delivered by a new Operate version. Every Operate version contains migration steps for the change as json files. Operate updates the current migration steps repository in Elasticsearch. The steps repository contains all available steps and its states.
By using this steps repository it is possible to apply needed steps to migrate from previous to current schema. Already applied and new steps will be detected and either applied
or ignored.

### How to migrate 

#### Migrate by using standalone application

Make sure that Elasticsearch that contains Operate data is running. The migration script will connect by default to specified connection in Operate settings.

Execute ```<application-directory>/bin/migrate```

First the current schema will be created if not exist. Then the migration takes place. If everything was successful the previous schema will be deleted. 
The application exits.


> **Note:** The old indices will be deleted after succeeded migration. That might need more disk space.

> **Important!** You should take care of data backup before performing migration.

#### Migrate by using automatic upgrade in Operate Webapplication

At the start of Operate webapplication the current schema will be created if not exist. If the automatic migration is enabled the migration will be started.
The migration runs only when ONE previous schema version exists. 

#### Configure migration

Automatic migration is enabled by default.
It can be enabled/disabled by set configuration key to true or false:

`camunda.operate.migration.migrationEnabled` 

To specify previous/source version use configuration key:

 `camunda.operate.migration.sourceVersion` 

If no *sourceVersion* is defined Operate tries to detect the previous version.


#### Example for migrate in Kubernetes

To ensure that the migration will be executed *before* operate will be started you can use
the [init container](https://kubernetes.io/docs/concepts/workloads/pods/init-containers/) feature of kubernetes. It makes sure that the 'main' container will only be started
if the initContainer was successfully executed. 
The following snippet of a pod description for kubernetes shows the usage of migration.sh as initContainer.

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


### File organization in schema folder

```
+ create
|   |
|   + index     - Folder of indices as json files
|   |
|   + template  - Folder of templates as json files
|   
+ change   - Folder of steps as json files
|   |
|   + step-file.json

``` 
