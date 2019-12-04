#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
VERSION=${project.version}
# For testing prefix 'echo ' 
RESTCLIENT="curl -K curl.config"

createNewTemplates(){
   for template in create/template/*.json; do
     templatename=`basename $template .json`
     echo "Create template $templatename"
     echo "-------------------------------"
 	 $RESTCLIENT --request PUT --url $ES/_template/${templatename}-${version} --data @$template
     echo
     echo "-------------------------------"
   done
}

createNewIndexes(){
   for index in create/index/*.json; do
     indexname=`basename $index .json`
     echo "Create index $indexname"
     echo "-------------------------------"
     $RESTCLIENT --request PUT --url $ES/${indexname}-${version}_ --data @$index
     echo
     echo "-------------------------------"
   done
}

createPipelines(){
	for pipeline in pipelines/*.json; do
    	pipelinename=`basename $pipeline .json`
    	echo "Create pipeline $pipelinename"
    	echo "-------------------------------"
    	$RESTCLIENT --request PUT --url $ES/_ingest/pipeline/$pipelinename --data @$pipeline
    	echo
    	echo "-------------------------------"
	done
}

removePipelines(){
	echo "Delete all pipelines that match operate-*-to-*"
	echo "-------------------------------"
	$RESTCLIENT --request DELETE --url $ES/_ingest/pipeline/operate-*-to-*
	echo
	echo "-------------------------------"
}

migrate(){
	for index in migrate/*.json; do
		echo "Migrate $index"
		echo "-------------------------------"
    	$RESTCLIENT --request POST --url $ES/_reindex?wait_for_completion=true --data @$index
    	echo
    	echo "-------------------------------"
	done
}


## main
createNewTemplates
createNewIndexes
#createPipelines
migrate
#removePipelines