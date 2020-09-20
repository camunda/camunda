# Introduction

Tasklist is a Spring Boot application. That means all ways to [configure](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
a Spring Boot application can be applied. By default the configuration for Tasklist is stored in a YAML file `application.yml`. All Tasklist related settings are prefixed
with `zeebe.tasklist`. The following parts are configurable:

 * [Elasticsearch Connection](#elasticsearch)
 * [Zeebe Broker connection](#zeebe-broker-connection)
 * [Zeebe Elasticsearch Exporter](#zeebe-elasticsearch-exporter)
 * [Authentication](authentication.md)
 * [Monitoring and health probes](#monitoring-and-health-probes)
 * [Logging configuration](#logging)

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

# Monitoring and health probes

Tasklist includes [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready) inside, that
provides number of monitoring possibilities., e.g. health check (http://localhost:8080/actuator/health) and metrics (http://localhost:8080/actuator/prometheus) endpoints.

Tasklist uses following Actuator configuration by default:
```yaml
# enable health check and metrics endpoints
management.endpoints.web.exposure.include: health,prometheus
# enable Kubernetes health groups:
# https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-kubernetes-probes
management.health.probes.enabled: true
```

With this configuration following endpoints are available for use out of the box:

```<server>:8080/actuator/prometheus``` Prometheus metrics

```<server>:8080/actuator/health/liveness``` Liveness probe

```<server>:8080/actuator/health/readiness``` Readiness probe

## Example snippets to use Tasklist probes in Kubernetes:

For details to set Kubernetes probes parameters see: [Kubernetes configure probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes)

### Readiness probe as yaml config:
```yaml
readinessProbe:
     httpGet:
        path: /actuator/health/readiness
        port: 8080
     initialDelaySeconds: 30
     periodSeconds: 30
```
### Liveness probe as yaml config:
```yaml
livenessProbe:
     httpGet:
        path: /actuator/health/liveness
        port: 8080
     initialDelaySeconds: 30
     periodSeconds: 30
```

# Logging

Tasklist uses Log4j2 framework for logging. In distribution archive as well as inside a Docker image `config/log4j2.xml` logging configuration files is included,
that can be further adjusted to your needs:

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
      <AppenderRef ref="${env:TASKLIST_LOG_APPENDER:-Console}"/>
    </Root>
  </Loggers>
</Configuration>
```

By default Console log appender will be used.

### JSON logging configuration

You can choose to output logs in JSON format (Stackdriver compatible). To enable it, define
the environment variable ```TASKLIST_LOG_APPENDER``` like this:

```sh
TASKLIST_LOG_APPENDER=Stackdriver
```

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
```


