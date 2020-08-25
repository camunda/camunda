# Introduction

Tasklist is a Spring Boot application. That means all ways to [configure](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
a Spring Boot application can be applied. By default the configuration for Tasklist is stored in a YAML file `application.yml`. All Tasklist related settings are prefixed
with `zeebe.tasklist`. The following parts are configurable:

 * [Elasticsearch Connection](#elasticsearch)
 * [Zeebe Broker connection](#zeebe-broker-connection)
 * [Zeebe Elasticsearch Exporter](#zeebe-elasticsearch-exporter)
 * [Authentication](authentication.md)
 * [Monitoring possibilities](#monitoring-tasklist)
 * [Logging configuration](#logging)
 * [Probes](#probes)

# Configurations

# Elasticsearch

Tasklist stores and reads data in/from Elasticsearch.

## Settings to connect

Name | Description | Default value
-----|-------------|--------------
zeebe.tasklist.elasticsearch.clusterName | Clustername of Elasticsearch | elasticsearch
zeebe.tasklist.elasticsearch.host | Hostname where Elasticsearch is running | localhost
zeebe.tasklist.elasticsearch.port | Port of Elasticsearch REST API | 9200

## A snippet from application.yml:

```yaml
zeebe.tasklist:
  elasticsearch:
    # Cluster name
    clusterName: elasticsearch
    # Host
    host: localhost
    # Transport port
    port: 9200
```

# Zeebe Broker Connection

Tasklist needs a connection to Zeebe Broker to start the import.

## Settings to connect

Name | Description | Default value
-----|-------------|--------------
zeebe.tasklist.zeebe.brokerContactPoint | Broker contact point to zeebe as hostname and port | localhost:26500

## A snippet from application.yml:

```yaml
zeebe.tasklist:
  zeebe:
    # Broker contact point
    brokerContactPoint: localhost:26500
```

# Zeebe Elasticsearch Exporter

Tasklist imports data from Elasticsearch indices created and filled in by [Zeebe Elasticsearch Exporter](https://github.com/zeebe-io/zeebe/tree/{{commit}}/exporters/elasticsearch-exporter).
Therefore settings for this Elasticsearch connection must be defined and must correspond to the settings on Zeebe side.

## Settings to connect and import:

Name | Description | Default value
-----|-------------|--------------
zeebe.tasklist.zeebeElasticsearch.clusterName | Cluster name of Elasticsearch | elasticsearch
zeebe.tasklist.zeebeElasticsearch.host | Hostname where Elasticsearch is running | localhost
zeebe.tasklist.zeebeElasticsearch.port | Port of Elasticsearch REST API | 9200
zeebe.tasklist.zeebeElasticsearch.prefix | Index prefix as configured in Zeebe Elasticsearch exporter | zeebe-record

## A snippet from application.yml:

```yaml
zeebe.tasklist:
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

# Monitoring Tasklist

Tasklist includes [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready) inside, that
provides number of monitoring possibilities, e.g. health check (http://localhost:8080/actuator/health) and metrics (http://localhost:8080/actuator/prometheus) endpoints.

Main Actuator configuration parameters are the following:

Name | Description | Default value
-----|-------------|--------------
management.endpoints.web.exposure.include | Spring boot [actuator endpoints](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-endpoints) to be exposed | health,prometheus
management.metrics.export.prometheus.enabled | When true, Prometheus metrics are enabled | true

## A snippet from application.yml

```yaml
#Spring Boot Actuator endpoints to be exposed
management.endpoints.web.exposure.include: health,prometheus
# Enable or disable metrics
management.metrics.export.prometheus.enabled: false
```

# Logging

Tasklist uses Log4j2 framework for logging. In distribution archive as well as inside a Docker image you can find two logging configuration files,
that can be further adjusted to your needs.

## Default logging configuration

* `config/log4j2.xml` (applied by default)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" monitorInterval="30">
  <Properties>
    <Property name="LOG_PATTERN">%clr{%d{yyyy-MM-dd HH:mm:ss.SSS}}{faint} %clr{%5p} %clr{${sys:PID}}{magenta} %clr{---}{faint} %clr{[%15.15t]}{faint} %clr{%-40.40c{1.}}{cyan} %clr{:}{faint} %m%n%xwEx</Property>
  </Properties>
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT" follow="true">
      <PatternLayout pattern="${LOG_PATTERN}"/>
    </Console>
	<Console name="Stackdriver" target="SYSTEM_OUT" follow="true">
      <StackdriverJSONLayout/>
    </Console>
  </Appenders>
  <Loggers>
    <Logger name="io.zeebe.tasklist" level="info" />
    <Root level="info">
      <AppenderRef ref="${env:Tasklist_LOG_APPENDER:-Console}"/>
    </Root>
  </Loggers>
</Configuration>
```
### JSON logging configuration

This one is specifically configured for usage with Stackdriver in Google Cloud
environment. The logs will be written in JSON format to make them better searchable in Google Cloud Logging UI.
## Enable Logging configuration

You can enable one of the logging configurations by setting the environment variable ```TASKLIST_LOG_APPENDER``` like this:

```sh
TASKLIST_LOG_APPENDER=Stackdriver
```

Default logging appender is Console.

# An example of application.yml file

The following snippet represents the default Tasklist configuration, which is shipped with the distribution. It can be found inside the `config` folder (`config/application.yml`)
 and can be used to adjust Tasklist to your needs.

```yaml
# Tasklist configuration file

zeebe.tasklist:
  # Set Tasklist username and password.
  # If user with <username> does not exists it will be created.
  # Default: demo/demo
  #username:
  #password:
  # ELS instance to store Tasklist data
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
#Spring Boot Actuator endpoints to be exposed
management.endpoints.web.exposure.include: health,info,conditions,configprops,prometheus
# Enable or disable metrics
#management.metrics.export.prometheus.enabled: false
```

# Probes

Tasklist provides liveness and readiness probes for using in cloud environment (Kubernetes).

* Kubernetes uses liveness probes to know when to restart a container.
* Kubernetes uses readiness probes to decide when the container is available for accepting traffic.

See also: [Kubernetes configure startup probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

A HTTP GET call to REST endpoint ```/actuator/health```can be used to make a liveness probe.
To use this call make sure that the management health endpoint is enabled in configuration file (`application.yml`):

```yaml
management.endpoints.web.exposure.include: health
```
See also [Monitoring possibilities](#monitoring-Tasklist)

A HTTP GET call to REST endpoint ```/api/check``` can be used to make a readiness probe.

Any HTTP status code greater than or equal to 200 and less than 400 indicates success. Any other code indicates failure.

In case you have Tasklist cluster and use dedicated Importer and/or Archiver nodes, you can use ```/actuator/health``` endpoint for both liveness and readiness probes for these nodes.

## Example snippets to use Tasklist probes in Kubernetes:
For details to set Kubernetes probes parameters see: [Kubernetes configure probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes)
### Readiness probe as yaml config:
```yaml
readinessProbe:
     httpGet:
        path: /api/check
        port: 8080
     initialDelaySeconds: 30
     periodSeconds: 30
```
### Liveness probe as yaml config:
```yaml
livenessProbe:
     httpGet:
        path: /actuator/health
        port: 8080
     initialDelaySeconds: 30
     periodSeconds: 30
```


