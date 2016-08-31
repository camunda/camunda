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

There are two major entry points:

* `WorkflowsClient workflowsClient = client.workflows()`: Provides access to all interactions related to BPMN processes and instances
* `AsyncTasksClient tasksClient = client.tasks()`: Provides access to all interactions related to client-handled tasks

## Workflows API

### Deploy a BPMN Process

```java
WorkflowDefinition workflowDefinition = workflowsClient
    .deploy()
    .resourceFile("path/to/bpmn.xml")
    .execute();
```

Process models may be read from a file, an input stream, a classpath resource or a BPMN Model API instance.

### Start a Workflow Instance

```java
WorkflowDefinition workflowDefinition = workflowsClient
    .start()
    .workflowDefinitionKey("foo")
    .execute();
```

Workflow instances can be started by key (=> `id` attribute of `process` element in XML) or ID.

## Tasks API

### Poll and Lock Task Instances

```java
LockedTasksBatch taskBatch = tasksClient.pollAndLock()
    .taskQueueId(0)
    .maxTasks(1)
    .taskType("print hello")
    .lockTime(10000L)
    .execute();
```

Properties:

* `taskQueueId`: Identifies the task queue this should be added to. Must correspond to a valid `id` of a `[task-queue]` element in the broker configuration
* `taskType`: Restricts the request to tasks of the given type. Corresponds to the value provided in the BPMN XML.

Limitations:

* Max tasks can only be 1
* Tasks with an expired lock time do not become lockable again

### Complete Task Instances

```java
tasksClient.complete()
    .taskQueueId(0)
    .taskId(lockedTask.getId())
    .execute();
```