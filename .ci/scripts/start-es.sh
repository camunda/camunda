#!/bin/sh

mkdir -p ../../backend/target/it-elasticsearch
cd ../../backend/target/it-elasticsearch
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.4.3.tar.gz
tar -xzvf ./elasticsearch-5.4.3.tar.gz
./elasticsearch-5.4.3/bin/elasticsearch -d
