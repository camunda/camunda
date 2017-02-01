@echo off

echo "starting Camunda Optimize ${project.version} with Elasticsearch ${elasticsearch.version}"

cd server\elasticsearch-${elasticsearch.version}\bin\
start elasticsearch.bat
 