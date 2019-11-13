#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix 'echo ' 
RESTCLIENT="curl"

backupOldIndexes(){
    for index in backup/*.json; do
        $RESTCLIENT --request POST --url ${ES}/_reindex?wait_for_completion=true --data @${index} --header 'Connection: keep-alive' --header 'Content-Type: application/json'
    done
}

deleteOldBackup(){
    $RESTCLIENT --request DELETE --url $ES/v1.0.0*operate-* --header 'Connection: keep-alive' --header 'Content-Type: application/json'
}

## Delete old index ( ALL that starts with operate- !! )
deleteOldIndexes(){
    $RESTCLIENT --request DELETE --url $ES/operate-* --header 'Connection: keep-alive' --header 'Content-Type: application/json'
}

deleteOldTemplates(){
    $RESTCLIENT --request DELETE --url $ES/_template/operate-* --header 'Connection: keep-alive' --header 'Content-Type: application/json'
}

createNewTemplates(){
    for template in create/template/*.json; do
      templatename=`basename $template .json`
      $RESTCLIENT --request PUT --url $ES/_template/$templatename --data @$template --header 'Connection: keep-alive' --header 'Content-Type: application/json'
    done
}

createNewIndexes(){
    for index in create/index/*.json; do
      indexname=`basename $index .json`
      $RESTCLIENT --request PUT --url $ES/$indexname --data @$index --header 'Connection: keep-alive' --header 'Content-Type: application/json'
    done
}

createPipelines(){
    for pipeline in pipelines/*.json; do
        pipelinename=`basename $pipeline .json`
        $RESTCLIENT --request PUT --url $ES/_ingest/pipeline/$pipelinename --data @$pipeline --header 'Connection: keep-alive' --header 'Content-Type: application/json'
    done
}

reindexOldToNew(){
    for index in reindex/*.json; do
        $RESTCLIENT --request POST --url $ES/_reindex?wait_for_completion=true --data @$index --header 'Connection: keep-alive' --header 'Content-Type: application/json'
    done
 }
### Run functions  ###
backupOldIndexes
#deleteOldTemplates
#deleteOldIndexes
#createNewTemplates
#createNewIndexes
#createPipelines
#sleep 5
#reindexOldToNew
#deleteOldBackup
