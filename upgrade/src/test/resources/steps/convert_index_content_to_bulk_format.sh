#!/bin/bash

#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
# Licensed under a proprietary license. See the License.txt file for more information.
# You may not use this file except in compliance with the proprietary license.
#
# Define the Elasticsearch host, index name and number of returned documents
host="http://localhost:9200"
# Add the index name that you want to dump here
index="optimize-single-process-report_v9"
size=1000

# Define the output file
output_file="39-singleprocessreport-index.json"

# Initialize the bulk_data variable
bulk_data=""

# Perform the search request
response=$(curl -s -X GET "$host/$index/_search?size=$size" -H 'Content-Type: application/json')

# Parse the documents from the response
docs=$(echo $response | jq -c '.hits.hits[] | {_source: ._source, _id: ._id, _type: ._type, _index: ._index}')

# Iterate over the documents
while read -r doc
do
    # Extract the index, type, and id of the document
    index=$(echo $doc | jq -r '._index')
    type=$(echo $doc | jq -r '._type')
    id=$(echo $doc | jq -r '._id')
    source=$(echo $doc | jq -c '._source')

    # Add the index, type, and id to the bulk_data variable
    bulk_data="$bulk_data{ \"index\" : { \"_index\" : \"$index\", \"_type\" : \"$type\", \"_id\" : \"$id\" } }\n"

    # Add the source field to the bulk_data variable
    bulk_data="$bulk_data$source\n"
done <<< "$docs"

# Removing leading and trailing whitespaces
bulk_data="$(echo "${bulk_data}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

# Write the bulk_data to the output file
echo -e $bulk_data > $output_file