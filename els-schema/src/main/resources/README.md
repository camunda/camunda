# Migration to ${project.version}

## Introduction 

To migrate operate Elasticsearch data to current version, you need a http/rest client like curl, wget, kibana etc.


The migration is organized in several steps:

 1. Remove old templates
 2. Create new templates and indices
 3. Create pipelines that defines the migration from old index to new index (if needed).
 4. Reindex (migrate) from old index into new index with usage of pipelines and delete old index (for each index).
 4. Delete pipelines 

There exists directories for each steps. These directories contains for every index/template a json request payload file.
The _create_ directory contains **index** and **template** settings and descriptions. 
The _migrate_ directory contains **pipeline** definitions (if needed) and **reindex** requests in pipeline and reindex folders
This makes it possible to customize definitions and settings like shards and replicas. Please refer Elasticsearch documentation for details.

The schema/format of the json files is according to [Elasticsearch REST API](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/index.html)  description.

The shell script execute every needed step.They are defined as shell functions. _migrate.sh_ uses 'curl' as HTTP client. It reads JSON request payload files and execute the via REST API of elasticsearch. 

## The steps in detail

### Delete old templates

 * To make sure that old templates can't interfere with creating new indexes it is needed to delete the old templates first.

### Create new templates and indices 

* Add for each index/template that should be added a **create index/template request** payload as json file in _create/index_ 
  and _create/template_ folder.
* The index/template names are derived from the json files. 
* The function createNewTemplates takes every file in folder create/template to execute a **PUT template** request.
* The function createNewIndex takes every file in folder create/index to execute a **PUT index** request.

### Create pipelines for migration 

* Add for each index that should be migrated a **create pipeline request** as payload in json file in _migration/pipeline_ folder 
* The names of the json files MUST be the same as the pipeline names.

### Reindex from old to new schema 

* Add for each index that should be converted a **reindex request** payload as json file in _migration/reindex_ folder
  
  Make sure you give the appropriate pipeline name in reindex request

### Delete pipelines 

* Define an pipeline name pattern that matches all the pipelines that should be deleted in **DELETE request**
 
  For example: operate-\*-to-\*
  
## Check results of migration

The script prints its requests and the responses from/to Elasticsearch.

After the execution of the steps in the appropriate order, there should exists new templates and indices with data converted 
from old schema. 

You can check with:

* GET templates:

`GET /_template/`

* GET indices:

`GET /_cat/indices/`

* GET aliases:

`GET /_cat/aliases/`

* GET data from indices:

`GET /<indexname>/_search`
