# Schema & Migration

Operate stores and reads data with elasticsearch. 

* [Schema](#schema)
* [Migration](#migration)
* [Migration procedure](#migration-procedure)
* [How to migrate](#how-to-migrate)

## Schema

Operate uses several indices that are mostly created by using templates.  
For each index exists a JSON file that describes how the index is structured and how it is configured. These JSON files
are following the elasticsearch REST API format. Therefore it is possible to customize settings ( [Example of an index template](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/indices-templates.html))

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

## Migration

Migration supports the transformation of data from one Operate version to the next version (from Operate-1.2.0 on). The migration
uses elasticsearch [processors](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/ingest-processors.html) and [pipelines](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/pipeline.html) to reindex the data. A shellscript executes the steps.

### Migration procedure

1. Remove old templates
2. Create new templates and indices
3. Create pipelines
4. Migrate indices 
5. Remove pipelines and old indices 

### How to migrate

Make sure that elasticsearch that contains operate data is running. The migration script will connect by default to
**http://localhost:9200**. Otherwise you need to specifiy the address.

All needed files for migration are in schema folder of Operate. Follow the next steps to migrate:

1. Change to *schema* folder Operate 
2. Unpacking *camunda-operate-els-schema-<OPERATE-VERSION>.zip*
3. Execute *migration.sh* [elasticsearch host:port] Shellscript

#### Example of migration in a shell
```
cd schema
unzip camunda-operate-els-schema-1.2.0.zip
sh ./migrate.sh
```

All requests and responses are written to the console. Here you can see if the migration was successful.
