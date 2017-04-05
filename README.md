TNGP - The Next Generation Process Engine
=========================================

TNGP is a system capable of executing BPMN-based workflows that is optimized for high throughput and scalability. It follows a client-server model. The server is also called the *broker* in this context.

Maven coordinates
-----------------

### TNGP release repository

```
<repository>
  <id>tngp-releases</id>
  <name>TNGP Releases</name>
  <url>https://app.camunda.com/nexus/content/repositories/camunda-tngp/</url>
</repository>
```

### TNGP snapshot repository

```
<repository>
  <id>tngp-releases</id>
  <name>TNGP Releases</name>
  <url>https://app.camunda.com/nexus/content/repositories/camunda-tngp-snapshots/</url>
</repository>
```

### Broker distribution

```
<dependency>
  <groupId>org.camunda.tngp</groupId>
  <artifactId>tngp-distribution</artifactId>
  <version>1.0.0-alpha3</version>
  <type>zip</type>
</dependency>
```

### Java Client

```
<dependency>
  <groupId>org.camunda.tngp</groupId>
  <artifactId>tngp-client-java</artifactId>
  <version>1.0.0-alpha3</version>
  <type>zip</type>
</dependency>
```

Get Started
-----------

1. [Run the broker](docs/content/get-started/broker.md)
1. [Make your BPMN TNGP-ready](docs/content/get-started/bpmn.md)
1. [Build a Java client application](docs/content/get-started/java-client-library.md) and


Build from source
-----------------

Run `mvn clean install` from root folder. Obtain snapshots from local Maven repo.

Feedback
--------

Please use github issues.
