#!/bin/sh -e

mkdir -p ./backend/target/it-elasticsearch
cd ./backend/target/it-elasticsearch
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.4.3.tar.gz
tar -xzvf ./elasticsearch-5.4.3.tar.gz
cp ../../src/test/es_conf/elasticsearch.yml ./elasticsearch-5.4.3/config/
./elasticsearch-5.4.3/bin/elasticsearch -d
