#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix 'echo ' 
RESTCLIENT="curl -K curl.config"

for index in create/index/*.json; do
     indexname=`basename $index .json`
     echo "Delete index $indexname"
     echo "-------------------------------"
 	 $RESTCLIENT --request DELETE --url $ES/${indexname}_
     echo
     echo "-------------------------------"
done

$RESTCLIENT --request DELETE --url $ES/_template/operate-*


