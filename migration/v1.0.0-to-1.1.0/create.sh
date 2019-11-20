#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix 'echo ' 
RESTCLIENT="curl -K curl.config"

createNewTemplates(){
   for template in create/template/*.json; do
     templatename=`basename $template .json`
     echo "Create template $templatename"
     echo "-------------------------------"
     $RESTCLIENT --request PUT --url $ES/_template/${templatename} --data @$template
     echo
     echo "-------------------------------"
   done
}

createNewIndexes(){
   for index in create/index/*.json; do
     indexname=`basename $index .json`
     echo "Create index $indexname"
     echo "-------------------------------"
     $RESTCLIENT --request PUT --url $ES/${indexname} --data @$index
     echo
     echo "-------------------------------"
   done
}

## main
createNewTemplates
createNewIndexes

