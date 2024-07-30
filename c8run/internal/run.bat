

@echo off

set EXPECTED_JAVA_VERSION=21

set SUBCOMMAND=%~1

@REM Configuration file defaults overriden because upstream config doesn't export to elasticsearch
setx ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME "io.camunda.zeebe.exporter.ElasticsearchExporter"
setx ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL "http://localhost:9200"
setx ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX "zeebe-record"

if %SUBCOMMAND% == "start" (

)

if %SUBCOMMAND% == "stop" (

)

if %SUBCOMMAND% == "help" (

)
