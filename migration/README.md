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

For each step exists a directory and a shell script. These directories contains for every index/template a json request payload file.
Every shell script execute one step. The scripts use 'curl' as http client. They read json request payload files and execute the requests to elasticsearch. 
So it is possible to customize definitions, settings and so on. Please refer Elasticsearch documentation for details.
The shell script migration.sh executes all steps in right order.
It is also possible to execute steps separately. 
The schema/format of the json files is according to [Elasticsearch REST API](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/index.html)  description.

## The steps in detail

### Create backup of current indices

* Add for each index that should be backup a reindex request payload as json file in backup folder 
* The script *create-backup.sh* takes every file in the folder and execute it as reindex.

### Delete old templates/indices 

* Define a template/index name pattern that matches all the templates/indices that should be deleted in DELETE request 
  
  For example: operate-*_

* Use this pattern in the DELETE request in the script.
* The script *delete.sh* executes the delete request.

### Create new templates and indices 

* Add for each index/template that should be added a create index/template request payload as json file in create/index 
  and create/template folder.
* The names of the json files MUST be the same as the index/template names.
* The function createNewTemplates takes every file in folder create/template to execute a PUT template request.
* The function createNewIndex takes every file in folder create/index to execute a PUT index request.
* The script *create.sh* executes the put template and put index request.

### Create pipelines for migration 

* Add for each index that should be migrated a create pipeline request as payload in json file in pipelines folder 
* The names of the json files MUST be the same as the pipeline names.
* The script *pipeline.sh* takes every file in folder pipelines to execute a PUT pipeline request.

### Reindex from old to new schema 

* Add for each index that should be converted a reindex request payload as json file in reindex folder
  
  Make sure you give the appropriate pipeline name in reindex request

* The script *reindex.sh* takes every file in folder reindex and execute it as reindex request.

### Delete backup

* Define an index name pattern that matches all the backup indices that should be deleted in DELETE request 
 
  For example: operate-*-v.1.0.0
  
* The script *remove-backup.sh* executes the delete request.
  
## Check results of migration

The scripts output what they request and the responses from Elasticsearch.

After the execution of the steps in the appropriate order, there should exists new templates and indices with data converted 
from old schema. Additionally the pipelines are accessible in Elasticsearch.

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
