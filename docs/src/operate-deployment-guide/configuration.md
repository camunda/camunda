# Introduction

Operate is a spring boot application.That means all ways to [configure](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config) a spring boot application can be applied. The configuration for Operate is stored in an YAML file `application.yml`. All Operate related settings are prefixed with `camunda.operate`.
In the configuration it is possible to influence:

 * [Authentication](authentication.md)
 * [Archiver](importer-and-archiver.md)
 * Batch Operations
 * Cluster nodes
 * Elasticsearch 
 * Logging
 * Metrics
 * Zeebe broker connection
 
# Configuration

# An example of application.yml file

The following snippet represents the default Operate configuration, which is shipped with the distribution. It can be found inside the `config` folder (`config/application.yml`) and can be used to adjust Operate to your needs.

[Source on github](https://github.com/camunda/camunda-operate/blob/master/distro/src/main/config/application.yml)

```yaml
# Operate configuration file

camunda.operate:
  # Set operate username and password. 
  # If user with <username> does not exists it will be created.
  # username - Default: demo
  #username: demo
  # password - Default: demo
  #password: demo
  # Enable CSRF prevention for webapp - Default: true
  #csrfPreventionEnabled: true
  #
  # Enable modules of Operate to distribute it on different nodes:
  # Importer - Default: true
  #importerEnabled: true
  # Archiver - Default: true
  #archiverEnabled: true
  # Webapp - Default:true 
  #webappEnabled: true
  #  
  # ELS instance to store Operate data
  elasticsearch:
    # Name of cluster - Default: elasticsearch - https://www.elastic.co/guide/en/elasticsearch/reference/6.8/cluster.name.html
    clusterName: elasticsearch
    # Host
    host: localhost
    # Transport port
    port: 9200
  # Zeebe instance
  zeebe:
    # Broker contact point - https://docs.zeebe.io/introduction/install.html#exposed-ports
    brokerContactPoint: localhost:26500
  # ELS instance to export Zeebe data to - https://github.com/zeebe-io/zeebe/tree/{{commit}}/exporters/elasticsearch-exporter
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
# Enable or disable metrics - Default: true
#management.metrics.export.prometheus.enabled: false
``` 