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

* `WorkflowTopicClient workflowsClient = client.workflowTopic(int topicId)`: Provides access to workflow-related operations on the given topic, such as process instantiation
* `TaskTopicClient tasksClient = client.taskTopic(int topicId)`: Provides access to task-related operations on the given topic, such as task subscriptions
* `TopicClient topicClient = client.int topicId(int topicId)`: Provides access to general-purpose operations on the given topic, such as topic subscriptions

Then take it from there, the Javadoc should give an idea what each method does.
