#!/bin/sh -xe

DIRECTORY=./backend/target/it-elasticsearch/elasticsearch-5.4.3/_snapshots/

if [ -d "$DIRECTORY" ]; then
    tar -czvf snapshots.tar.gz ./backend/target/it-elasticsearch/elasticsearch-5.4.3/_snapshots/*
    mv ./snapshots.tar.gz ./backend/target/it-elasticsearch/elasticsearch-5.4.3/_snapshots/
    ls -la ./backend/target/it-elasticsearch/elasticsearch-5.4.3/
fi