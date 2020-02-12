# Introduction

Operate is a Spring Boot application. That means all ways to [configure](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config) 
a Spring Boot application can be applied. By default the configuration for Operate is stored in a YAML file `application.yml`. All Operate related settings are prefixed 
with `camunda.operate`. The following parts are configurable:

 * [Elasticsearch Connection](#elasticsearch)
 * [Zeebe Broker connection](#zeebe-broker-connection)
 * [Zeebe Elasticsearch Exporter](#zeebe-elasticsearch-exporter)
 * [Operation Executor](#operation-executor)
 * [Authentication](authentication.md)
 * [Scaling Operate](importer-and-archiver.md)
 * [Monitoring possibilities](#monitoring-operate)
 
# Configurations

# Elasticsearch

Operate stores and reads data in/from Elasticsearch.

## Settings to connect

Name | Description | Default value
-----|-------------|--------------
camunda.operate.elasticsearch.clusterName | Clustername of Elasticsearch | elasticsearch
camunda.operate.elasticsearch.host | Hostname where Elasticsearch is running | localhost
camunda.operate.elasticsearch.port | Port of Elasticsearch REST API | 9200

## A snippet from application.yml:

```
camunda.operate:
  elasticsearch:
    # Cluster name
    clusterName: elasticsearch
    # Host
    host: localhost
    # Transport port
    port: 9200
```

# Zeebe Broker Connection

Operate needs a connection to Zeebe Broker to start the import and to execute user operations.

## Settings to connect

Name | Description | Default value
-----|-------------|--------------
camunda.operate.zeebe.brokerContactPoint | Broker contact point to zeebe as hostname and port | localhost:26500

## A snippet from application.yml:

```
camunda.operate:  
  zeebe:
    # Broker contact point
    brokerContactPoint: localhost:26500
```

# Zeebe Elasticsearch Exporter

Operate imports data from Elasticsearch indices created and filled in by [Zeebe Elasticsearch Exporter](https://github.com/zeebe-io/zeebe/tree/{{commit}}/exporters/elasticsearch-exporter).
Therefore settings for this Elasticsearch connection must be defined and must correspond to the settings on Zeebe side.

## Settings to connect and import:

Name | Description | Default value
-----|-------------|--------------
camunda.operate.zeebeElasticsearch.clusterName | Cluster name of Elasticsearch | elasticsearch
camunda.operate.zeebeElasticsearch.host | Hostname where Elasticsearch is running | localhost
camunda.operate.zeebeElasticsearch.port | Port of Elasticsearch REST API | 9200
camunda.operate.zeebeElasticsearch.prefix | Index prefix as configured in Zeebe Elasticsearch exporter | zeebe-record

## A snippet from application.yml:

```
camunda.operate:
  zeebeElasticsearch:
    # Cluster name
    clusterName: elasticsearch
    # Host
    host: localhost
    # Transport port
    port: 9200
    # Index prefix, configured in Zeebe Elasticsearch exporter
    prefix: zeebe-record
```

# Operation Executor

Operations are user operations like Cancellation of workflow instance(s) or Updating the variable value. Operations are executed in multi-threaded manner. 

Name | Description | Default value
-----|-------------|--------------
camunda.operate.operationExecutor.threadsCount| How many threads should be used | 3

## A snippet from application.yml

```
camunda.operate:
  operationExecutor:
  	threadsCount: 3
```

# Monitoring Operate

Operate includes [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready) inside, that 
provides number of monitoring possibilities, e.g. health check (http://localhost:8080/actuator/health) and metrics (http://localhost:8080/actuator/prometheus) endpoints.

Main Actuator configuration parameters are the following:

Name | Description | Default value
-----|-------------|--------------
management.endpoints.web.exposure.include | Spring boot [actuator endpoints](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-endpoints) to be exposed | health,prometheus
management.metrics.export.prometheus.enabled | When true, Prometheus metrics are enabled | true

## A snippet from application.yml

```
#Spring Boot Actuator endpoints to be exposed
management.endpoints.web.exposure.include: health,prometheus
# Enable or disable metrics
management.metrics.export.prometheus.enabled: false
```

# An example of application.yml file

The following snippet represents the default Operate configuration, which is shipped with the distribution. It can be found inside the `config` folder (`config/application.yml`)
 and can be used to adjust Operate to your needs.

```yaml
# Operate configuration file

camunda.operate:
  # Set operate username and password. 
  # If user with <username> does not exists it will be created.
  # Default: demo/demo
  #username:
  #password:
  # ELS instance to store Operate data
  elasticsearch:
    # Cluster name
    clusterName: elasticsearch
    # Host
    host: localhost
    # Transport port
    port: 9200
  # Zeebe instance
  zeebe:
    # Broker contact point
    brokerContactPoint: localhost:26500
  # ELS instance to export Zeebe data to
  zeebeElasticsearch:
    # Cluster name
    clusterName: elasticsearch
    # Host
    host: localhost
    # Transport port
    port: 9200
    # Index prefix, configured in Zeebe Elasticsearch exporter
    prefix: zeebe-record
logging:
  level:
    ROOT: INFO
    org.camunda.operate: INFO
#Spring Boot Actuator endpoints to be exposed
management.endpoints.web.exposure.include: health,info,conditions,configprops,prometheus
# Enable or disable metrics
#management.metrics.export.prometheus.enabled: false
``` 
