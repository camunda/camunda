#!/bin/sh

echo "Start migration."
# Delete current index
./create.sh
# Why is this needed ?
sleep 5
# Fill new index with data from old index + pipeline   
./reindex.sh
echo "Finished migration."