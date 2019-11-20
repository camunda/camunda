#!/bin/sh
# Define migration functions
#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix 'echo ' 
RESTCLIENT="curl -K curl.config"

for index in backup/*.json; do
   indexname=`basename $index .json`
   echo "Save index ${indexname}_ to ${indexname}-1.1.0_"
   echo "-------------------------------"
   $RESTCLIENT --request POST --url ${ES}/_reindex?wait_for_completion=true --data @${index}
   echo
   echo "-------------------------------" 
done
