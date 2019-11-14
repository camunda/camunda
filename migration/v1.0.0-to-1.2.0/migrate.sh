#!/bin/sh

echo "Start migration."
# Create backup of current index
./create-backup.sh
# Delete current index
./delete.sh
# Create new templates and indices
./create.sh
# Create pipelines
./pipeline.sh
# Why is this needed ?
sleep 5
# Fill new index with data from old index + pipeline   
./reindex.sh
# Remove backup of current index
./remove-backup.sh
echo "Finished migration."