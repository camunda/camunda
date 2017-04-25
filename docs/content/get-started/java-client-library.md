---

title: "Java Client"
weight: 40

menu:
  main:
    identifier: "java-client"
    parent: "get-started"

---

# Setup

## Prerequisites

* Java 8

## Usage in a Maven project

In order to use the Java client library, declare the following Maven dependency in your project:

```xml
<dependency>
  <groupId>org.camunda.tngp</groupId>
  <artifactId>tngp-client-java</artifactId>
  <version>${tngp.version}</version>
</dependency>
```

The version of the client should always match the broker's version.

## Logging

The client uses SLF4J for logging. It logs useful things like exception stack traces, when a task handler fails execution. Using SLF4J, any SLF4J implementation can be used. The following uses Log4J 2.

### Maven dependencies

```xml
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-slf4j-impl</artifactId>
  <version>2.8.1</version>
</dependency>

<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-core</artifactId>
  <version>2.8.1</version>
</dependency>
```

### Configuration

Add a file called `log4j2.xml` to the classpath of your application. Add the following contents:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" strict="true"
    xmlns="http://logging.apache.org/log4j/2.0/config"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://logging.apache.org/log4j/2.0/config https://raw.githubusercontent.com/apache/logging-log4j2/log4j-2.8.1/log4j-core/src/main/resources/Log4j-config.xsd">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level Java Client: %logger{36} - %msg%n"/>
    </Console>
  </Appenders>
  <Loggers>
    <Root level="info">
      <AppenderRef ref="Console"/>
    </Root>
  </Loggers>
</Configuration>
```

This will log every log message to console.

## Bootstrapping

In Java code, instantiate the client as follows:

```java
Properties clientProperties = new Properties();
clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, "127.0.0.1:51015");

TngpClient client = TngpClient.create(clientProperties);
```

See the class `org.camunda.tngp.client.ClientProperties` for a description of all client configuration properties.

# API

Entry points:

* `WorkflowTopicClient workflowsClient = client.workflowTopic(String topicName, int partitionId)`: Provides access to workflow-related operations on the given topic, such as process instantiation
* `TaskTopicClient tasksClient = client.taskTopic(String topicName, int partitionId)`: Provides access to task-related operations on the given topic, such as task subscriptions
* `TopicClient topicClient = client.topic(String topicName, int partitionId)`: Provides access to general-purpose operations on the given topic, such as topic subscriptions

Then take it from there, the Javadoc should give an idea what each method does.

