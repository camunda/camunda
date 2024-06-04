This is a distribution of Camunda ${project.version}

How to run
==========

Prerequisites:
1. Download Elasticsearch ${version.elasticsearch} (from https://www.elastic.co/downloads/elasticsearch)
2. For non-production cases, disable Elasticsearch's security packages by setting the xpack.security.* configuration options to false in ELASTICSEARCH_HOME/config/elasticsearch.yml
3. Start Elasticsearch by running ELASTICSEARCH_HOME/bin/elasticsearch (or ELASTICSEARCH_HOME\bin\elasticsearch.bat on Windows)

Start Camunda:
1. Run bin/camunda (or bin\camunda.bat on Windows)
