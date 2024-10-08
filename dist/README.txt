This is a distribution of Camunda ${project.version}

How to run
==========

# With Elasticsearch:

## Prerequisites:

1. Download Elasticsearch ${version.elasticsearch} (from https://www.elastic.co/downloads/elasticsearch)
2. For non-production cases, disable Elasticsearch's security packages by adding the configuration "xpack.security.enabled: false" in $ELASTICSEARCH_HOME/config/elasticsearch.yml
3. Start Elasticsearch by running $ELASTICSEARCH_HOME/bin/elasticsearch (or $ELASTICSEARCH_HOME\bin\elasticsearch.bat on Windows)

## Start Camunda:

1. Add the Elasticsearch exporter configuration in the configuration file config/application.yml

zeebe:
broker:
exporters:
elasticsearch:
className: io.camunda.zeebe.exporter.ElasticsearchExporter
args:
url: http://localhost:9200

2. Run bin/camunda (or bin\camunda.bat on Windows)

# With Postgres:

## Prerequisites:

Start Postgres docker container:

```
docker-compose up -d postgres
```

## Start Camunda:

Run camunda with `rdbmsPostgres` profile

# With MariaDB:

## Prerequisites:

Start Postgres docker container:

```
docker-compose up -d mariabb
```

## Start Camunda:

Run camunda with `rdbmsMariaDB` profile

