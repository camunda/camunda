#!/bin/sh -e

mkdir -p ./backend/target/it-elasticsearch
cd ./backend/target/it-elasticsearch
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-6.0.0.tar.gz
tar -xzvf ./elasticsearch-6.0.0.tar.gz
cp ../../src/test/es_conf/elasticsearch.yml ./elasticsearch-6.0.0/config/
./elasticsearch-6.0.0/bin/elasticsearch -d
