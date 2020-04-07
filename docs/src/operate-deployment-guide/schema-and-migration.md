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
version of Operate to another, migration of data must be performed. Operate distribution provides scripts to perform data migration strictly from previous minor. 
Operate version to the next minor version, meaning that if you make bigger upgrade step, e.g. 1.2.0 -> 1.4.0, migration must be performed step by step for each minor version:
1.2.0 -> 1.3.0 and 1.3.0 -> 1.4.0.

Information to migrate schema from v. x.y-1.z to x.y.z (sometimes from x-1.y.z to x.0.0) can be found in the latter version distribution in `schema/migrate` folder.

The migration uses Elasticsearch [processors](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/ingest-processors.html) and [pipelines](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/pipeline.html) to reindex the data. 
All required Elasticsearch scripts are provided in `schema/migrate` folder of the distribution. Additionally example shell script (`migrate.sh`) is provided that executes the following steps:

1. Remove old templates
2. Create new templates and indices
3. Create pipelines
4. Migrate data 
5. Remove pipelines and old indices 

### How to migrate

Make sure that Elasticsearch that contains Operate data is running. The migration script will connect by default to
**http://localhost:9200**. Otherwise you need to specify the address.

Follow the next steps to migrate:

1. Change to *schema* folder Operate 
2. Extract *camunda-operate-els-schema-<OPERATE-VERSION>.zip*
3. Execute *migration.sh* ``[elasticsearch host:port]`` shell script

```
cd schema
unzip camunda-operate-els-schema-1.2.0.zip
bash ./migrate.sh
```

All requests and responses are written to the console. Here you can see if the migration was successful.

> **Note:** You might need to adjust `migrate.sh` script to suit your needs.

> **Note:** The old indices will be deleted after succeeded migration. That might need more disk space.

> **Important!** You should take care of data backup before performing migration.

### Example for migrate in Kubernetes

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
       image: camunda/operate:0.23.0
       command: ['/bin/bash','/usr/local/operate/migration/migrate.sh','http://elasticsearch-host:9200']
   containers:
     - name: operate
       image: camunda/operate:0.23.0
       env:
...
```


### File organization in schema folder

```
+ create
|   |
|   + index     - Indices
|   |
|   + template  - Templates
|   
+ migrate
|   |
|   + pipeline  - Pipelines
|   | 
|   + reindex   - Reindex requests
|
+ migrate.sh    

``` 
