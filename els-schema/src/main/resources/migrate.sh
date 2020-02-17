#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix with 'echo ' 
RESTCLIENT="curl -s -K curl.config"

error_exit(){
   msg=$1 || "Unknown error"
   echo "Couldn't migrate: $msg" 
   exit 1
}

checkElasticsearchConnection(){
  $RESTCLIENT --request GET --url $ES/_cluster/health &> /dev/null || error_exit "Connection to $ES failed"
}

checkIsCurrentVersionAlreadyInstalled(){
  $RESTCLIENT --request GET --url $ES/operate-*-${schema.version}* | grep "${schema.version}" &> /dev/null && error_exit "Schema version '${schema.version}' exists already."
}

createNewTemplatesAndTheirIndexes(){
   for template in create/template/*.json; do
     templatename=`basename $template .json`
     full_templatename=${templatename}-${schema.version}_template
     full_indexname=${templatename}-${schema.version}_
     echo "Create template ${full_templatename} and index ${full_indexname}"
     echo "-------------------------------"
 	 $RESTCLIENT --request PUT --url $ES/_template/${full_templatename}?include_type_name=false --data @$template || error_exit "Failed to create template $full_templatename"
 	 $RESTCLIENT --request PUT --url $ES/${full_indexname} || error_exit "Failed to create index $full_indexname"
     echo
     echo "-------------------------------"
   done
}

createNewIndexes(){
   for index in create/index/*.json; do
     indexname=`basename $index .json`
     full_indexname=${indexname}-${schema.version}_
     echo "Create index ${full_indexname}"
     echo "-------------------------------"
     $RESTCLIENT --request PUT --url $ES/${full_indexname}?include_type_name=false --data @$index || error_exit "Failed to create index $full_indexname"
     echo
     echo "-------------------------------"
   done
}

createPipelines(){
	for pipeline in migrate/pipeline/*.json; do
    	pipelinename=`basename $pipeline .json`
    	echo "Create pipeline $pipelinename"
    	echo "-------------------------------"
    	$RESTCLIENT --request PUT --url $ES/_ingest/pipeline/$pipelinename --data @$pipeline || error_exit "Failed to create pipeline $pipelinename"
    	echo
    	echo "-------------------------------"
	done
}

removePipelines(){
	echo "Delete all pipelines that match operate-*"
	echo "-------------------------------"
	$RESTCLIENT --request DELETE --url $ES/_ingest/pipeline/operate-* || error_exit "Failed to delete pipelines that match operate-*"
	echo
	echo "-------------------------------"
}

removeOldTemplates(){
	echo "Delete all old templates"
	for template in create/template/*.json; do
		templatename=`basename $template .json`
		full_templatename=${templatename}-${schema.previous_version}_template
		echo "Delete old templates ${full_templatename}"
		echo "-------------------------------"
		$RESTCLIENT --request DELETE --url $ES/_template/${full_templatename} || error_exit "Failed to delete $full_templatename"
		echo
    	echo "-------------------------------"
	done
}

migrate(){
	echo "Migrate indices (reindex old to new index and delete old index)"
	for index in migrate/reindex/*.json; do
		indexname=`basename $index .json`
		echo "Migrate $indexname "
		echo "-------------------------------"
    	$RESTCLIENT --request POST --url $ES/_reindex?wait_for_completion=true --data @$index || error_exit "Failed to reindex $indexname"
    	echo
    	echo "Delete ${indexname}-${schema.previous_version}* "
		echo "-------------------------------"
    	$RESTCLIENT --request DELETE --url $ES/${indexname}-${schema.previous_version}* || error_exit "Failed to delete indices that match ${indexname}-${schema.previous_version}*"
    	echo
    	echo "-------------------------------"
	done
}

## main
checkElasticsearchConnection
#checkIsCurrentVersionAlreadyInstalled
removeOldTemplates
createNewIndexes
createNewTemplatesAndTheirIndexes
createPipelines
migrate
removePipelines