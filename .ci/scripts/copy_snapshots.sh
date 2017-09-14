#!/bin/sh -xe

DIRECTORY=./backend/target/it-elasticsearch/_snapshots/

if [ -d "$DIRECTORY" ]; then
    echo "folder found, copying resources"
    tar -czvf snapshots.tar.gz ./backend/target/it-elasticsearch/_snapshots/*
    mv ./snapshots.tar.gz ./backend/target/it-elasticsearch/_snapshots/
fi