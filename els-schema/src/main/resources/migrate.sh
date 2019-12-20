#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix with 'echo ' 
RESTCLIENT="curl -K curl.config"

createNewTemplatesAndTheirIndexes(){
   for template in create/template/*.json; do
     templatename=`basename $template .json`
     echo "Create template $templatename"
     echo "-------------------------------"
 	 $RESTCLIENT --request PUT --url $ES/_template/${templatename}-${schema.version}?include_type_name=false --data @$template
 	 $RESTCLIENT --request PUT --url $ES/${templatename}-${schema.version}_
     echo
     echo "-------------------------------"
   done
}

createNewIndexes(){
   for index in create/index/*.json; do
     indexname=`basename $index .json`
     echo "Create index $indexname"
     echo "-------------------------------"
     $RESTCLIENT --request PUT --url $ES/${indexname}-${schema.version}_?include_type_name=false --data @$index
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
		echo "-------------------------------"
		$RESTCLIENT --request DELETE --url $ES/_template/${templatename}_template
done
	echo
	echo "-------------------------------"
}

migrate(){
	echo "Migrate indices ( reindex old to new index and delete old index)"
	for index in migrate/reindex/*.json; do
		indexname=`basename $index .json`
		echo "Migrate $index "
		echo "-------------------------------"
    	$RESTCLIENT --request POST --url $ES/_reindex?wait_for_completion=true --data @$index
    	$RESTCLIENT --request DELETE --url $ES/${indexname}_ 
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