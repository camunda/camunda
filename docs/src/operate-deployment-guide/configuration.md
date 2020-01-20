# Introduction

Operate is a spring boot application.That means all ways to [configure](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config) a spring boot application can be applied. The configuration for Operate is stored in an YAML file `application.yml`. All Operate related settings are prefixed with `camunda.operate`.
The following parts are configurable:

 * [Authentication](authentication.md)
 * [Archiver and Importer](importer-and-archiver.md)
 * [Elasticsearch](#elasticsearch)
 * [Metrics](#metrics)
 * [Operation Executor](#operation-executor)
 * [Zeebe Broker connection](#zeebe-broker-connection)
 * [Zeebe Elasticsearch Exporter](#zeebe-elasticsearch-exporter)
 
# Configurations

# Elasticsearch

Operate stores and reads data in elasticsearch

## Settings to connect

Name | Description | Default value
-----|-------------|--------------
camunda.operate.elasticsearch.clusterName | Clustername of elasticsearch | elasticsearch
camunda.operate.elasticsearch.host | Hostname where elasticsearch is running | localhost
camunda.operate.elasticsearch.port | Port of elasticsearch service | 9200

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
# Metrics 

Operate provides health and metric endpoints. 

These endpoints can enabled by settings.

Name | Description | Default value
-----|-------------|--------------
management.endpoints.web.exposure.include | Spring boot [actuator endpoints](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-endpoints) to be exposed | health,info,conditions,configprops,prometheus
management.metrics.export.prometheus.enabled| Operate metrics to be enabled | true

## A snippet from application.yml

```
#Spring Boot Actuator endpoints to be exposed
management.endpoints.web.exposure.include: health,info,conditions,configprops,prometheus
# Enable or disable metrics
management.metrics.export.prometheus.enabled: false
```


# Operation Executor

Operations are executed by threads. 

Name | Description | Default value
-----|-------------|--------------
camunda.operate.operationExecutor.threadsCount| How many threads should be used| 3

## A snippet from application.yml

```
camunda.operate:
  operationExecutor:
  	threadsCount: 3
```
 
# Zeebe Broker Connection

Operate needs a connection to Zeebe Broker to execute operations.

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

Operate imports data from Zeebe Elasticsearch [Exporter](https://github.com/zeebe-io/zeebe/tree/f81fc87e5122d89c4850b844054eee48f26c4b29/exporters/elasticsearch-exporter).

## Settings to connect and import:

Name | Description | Default value
-----|-------------|--------------
camunda.operate.zeebeElasticsearch.clusterName | Clustername of elasticsearch | elasticsearch
camunda.operate.zeebeElasticsearch.host | Hostname where elasticsearch is running | localhost
camunda.operate.zeebeElasticsearch.port | Port of elasticsearch service | 9200
camunda.operate.zeebeElasticsearch.prefix | Index prefix as configured in zeebe elasticsearch exporter | zeebe-record

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
 

# An example of application.yml file

The following snippet represents the default Operate configuration, which is shipped with the distribution. It can be found inside the `config` folder (`config/application.yml`) and can be used to adjust Operate to your needs.

[Source on github](https://github.com/camunda/camunda-operate/blob/master/distro/src/main/config/application.yml)

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