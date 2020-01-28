#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix with 'echo ' 
RESTCLIENT="curl -K curl.config"

createNewTemplatesAndTheirIndexes(){
   for template in create/template/*.json; do
     templatename=`basename $template .json`
     full_templatename=${templatename}-${schema.version}
     full_indexname=${full_templatename}_
     echo "Create template ${templatename}-${schema.version} and index ${templatename}-${schema.version}_"
     echo "-------------------------------"
 	 $RESTCLIENT --request PUT --url $ES/_template/${full_templatename}?include_type_name=false --data @$template
 	 $RESTCLIENT --request PUT --url $ES/${full_indexname}
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
     $RESTCLIENT --request PUT --url $ES/${full_indexname}?include_type_name=false --data @$index
     echo
     echo "-------------------------------"
   done
}

createPipelines(){
	for pipeline in migrate/pipeline/*.json; do
    	pipelinename=`basename $pipeline .json`
    	echo "Create pipeline $pipelinename"
    	echo "-------------------------------"
    	$RESTCLIENT --request PUT --url $ES/_ingest/pipeline/$pipelinename --data @$pipeline
    	echo
    	echo "-------------------------------"
	done
}

removePipelines(){
	echo "Delete all pipelines that match operate-*"
	echo "-------------------------------"
	$RESTCLIENT --request DELETE --url $ES/_ingest/pipeline/operate-*
	echo
	echo "-------------------------------"
}

removeOldTemplates(){
	echo "Delete all old templates"
	for template in create/template/*.json; do
		templatename=`basename $template .json`
		full_templatename=${templatename}-${schema.old_version}_template
		echo "Delete old templates ${full_templatename}"
		echo "-------------------------------"
		$RESTCLIENT --request DELETE --url $ES/_template/${full_templatename}
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
    	$RESTCLIENT --request POST --url $ES/_reindex?wait_for_completion=true --data @$index
    	echo
    	echo "Delete ${indexname}-${schema.old_version}* "
		echo "-------------------------------"
    	$RESTCLIENT --request DELETE --url $ES/${indexname}-${schema.old_version}*
    	echo
    	echo "-------------------------------"
	done
}

## main
removeOldTemplates
createNewIndexes
createNewTemplatesAndTheirIndexes
createPipelines
migrate
removePipelines