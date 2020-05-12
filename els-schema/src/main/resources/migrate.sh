#!/bin/bash
# change to folder where migration.sh exists
cd $(dirname $0)	
# Define migration functions
ES=${1:-http://localhost:9200}
echo "Use elasticsearch server $ES"
# For testing prefix with 'echo ' 
RESTCLIENT="curl -s -K curl.config"

error_exit(){
   msg=$1 || "Unknown error"
   echo "Couldn't migrate: $msg" 
   exit 1
}

success_exit(){
	msg=$1 || "Success. Exit."
	echo $msg
	exit 0
}

checkElasticsearchConnection(){
  $RESTCLIENT --request GET --url $ES/_cluster/health &> /dev/null || error_exit "Connection to $ES failed."
}

checkNoSchemaExists(){
  $RESTCLIENT --request GET --url $ES/tasklist-* | grep "tasklist" &> /dev/null || success_exit "No tasklist schema exists."
}

checkIsPreviousVersionInstalled(){
  $RESTCLIENT --request GET --url $ES/tasklist-*-${schema.previous_version}* | grep "${schema.previous_version}" &> /dev/null || error_exit "Can't migrate current schema to '${schema.version}'. Only migration from ${schema.previous_version} to ${schema.version} is supported."
}

checkIsCurrentVersionAlreadyInstalled(){
  $RESTCLIENT --request GET --url $ES/tasklist-*-${schema.version}* | grep "${schema.version}" &> /dev/null && success_exit "Schema version '${schema.version}' exists already."
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
	shopt -s nullglob
	for pipeline in migrate/pipeline/*.json; do
    	pipelinename=`basename $pipeline .json`
    	echo "Create pipeline $pipelinename"
    	echo "-------------------------------"
    	$RESTCLIENT --request PUT --url $ES/_ingest/pipeline/$pipelinename --data @$pipeline || error_exit "Failed to create pipeline $pipelinename"
    	echo
    	echo "-------------------------------"
	done
	shopt -u nullglob
}

removePipelines(){
	echo "Delete all pipelines that match tasklist-*"
	echo "-------------------------------"
	$RESTCLIENT --request DELETE --url $ES/_ingest/pipeline/tasklist-* || error_exit "Failed to delete pipelines that match tasklist-*"
	echo
	echo "-------------------------------"
}

removeOldTemplates(){
	shopt -s nullglob
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
	shopt -u nullglob
}

migrate(){
	echo "Migrate indices (reindex old to new index and delete old index)"
	for index in migrate/reindex/*.json; do
		indexname=`basename $index .json`
		echo "Migrate $indexname "
		echo "-------------------------------"
    	$RESTCLIENT --request POST --url $ES/_reindex?wait_for_completion=true --data @$index || error_exit "Failed to reindex $indexname"
    	echo
    	echo "-------------------------------"
	done
}

deleteOldIndices(){
	echo "Delete old indices"
	for index in migrate/reindex/*.json; do
		indexname=`basename $index .json`
    	echo "Delete ${indexname}-${schema.previous_version}* "
		echo "-------------------------------"
    	$RESTCLIENT --request DELETE --url $ES/${indexname}-${schema.previous_version}* || error_exit "Failed to delete indices that match ${indexname}-${schema.previous_version}*"
    	echo
    	echo "-------------------------------"
	done
}

## main

# checks for schema
checkElasticsearchConnection
checkNoSchemaExists
checkIsCurrentVersionAlreadyInstalled
checkIsPreviousVersionInstalled 
# start migration
removeOldTemplates
createNewIndexes
createNewTemplatesAndTheirIndexes
createPipelines
migrate
deleteOldIndices
removePipelines