# Migration 

## Introduction 

To migrate operate Elasticsearch data from one to another version, you need a http/rest client like curl, wget, kibana etc.
For every version to version migration exists a directory that contains all necessary files.

The migration is organized in several steps:

 1. Create a backup of current indices (only until version 1.2.0 needed)
 2. Delete current templates and indices (only until version 1.2.0 needed)
 3. Create new templates and indices
 4. Create pipelines that defines the migration from current/old index to new index
 5. Reindex from current/old index into new index with usage of pipelines
 6. Delete backup of current/old index (only until version 1.2.0 needed)

For each step exists a directory. These directories contains for every index/template a json request payload file.
In migrate.sh are functions defined, that use 'curl' as http client. These functions reads json request payload files and execute the requests to elasticsearch. 
So it is possible to customize definitions, settings and so on. Please refer Elasticsearch documentation for details.
The shell script migration.sh shows also the order of steps, that will be executed in migration.
It is also possible to execute steps separately. The migration.sh just shows an example for how to make a migration.
The schema/format of the json files is according to [elasticsearch REST API](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/index.html)  description.

## The steps in detail

### Create backup of current indices

* Add for each index that should be backup a reindex request payload as json file in backup folder 
* The function backupOldindices takes every file in the folder and execute it as reindex.

### Delete old templates/indices 

* Define a template/index name pattern that matches all the templates/indices that should be deleted in DELETE request 
  
  For example: operate-*_

* Use this pattern in the DELETE request in deleteOldTemplate function for templates
* Use this pattern in the DELETE request in deleteOldIndex function for indices

### Create new templates and indices 

* Add for each index/template that should be added a create index/template request payload as json file in create/index 
  and create/template folder.
* The names of the json files MUST be the same as the index/template names.
* The function createNewTemplates takes every file in folder create/template to execute a PUT template request.
* The function createNewIndex takes every file in folder create/index to execute a PUT index request.

### Create pipelines for migration 

* Add for each index that should be migrated a create pipeline request as payload in json file in pipelines folder 
* The names of the json files MUST be the same as the pipeline names.
* The function createPipelines takes every file in folder pipelines to execute a PUT pipeline request.

### Reindex from old to new schema 

* Add for each index that should be converted a reindex request payload as json file in reindex folder
  
  Make sure you give the appropriate pipeline name in reindex request

* The function reindexOldToNew takes every file in folder reindex and execute it as reindex request.

### Delete backup

* Define an index name pattern that matches all the backup indices that should be deleted in DELETE request 
 
  For example: operate-*-v.1.0.0
  
## Check results of migration

After the execution of the steps in the appropriate order, should exists the new templates and indices with data converted 
from old schema. Additionally the pipelines are accessible in elasticsearch.

You can check with:

* GET templates:

`GET /_template/`

* GET indices:

`GET /_cat/indices/`

* GET aliases:

`GET /_cat/aliases/`

* GET pipelines:

`GET /_ingest/pipeline`

* GET data from indices:

`GET /<indexname>/_search`
