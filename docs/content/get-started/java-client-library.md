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


## Task Subscriptions API

*Task subscriptions* can be used to implement task execution in an event-driven way. Once a subscription is opened, the client then receives future tasks for that type,
locks them and hands them to a registered *task handler*.

### Creating a Subscription

```java
TaskHandler sendInvoiceHandler = new SendInvoiceHandler();

TaskSubscription subscription = tasksClient.newSubscription()
    .taskQueueId(0)
    .taskType("send-invoice")
    .lockTime(100000L)
    .handler(sendInvoiceHandler)
    .open();
```

Whenever a new task is received, the `sendInvoiceHandler` object receives that task and can process it.

Properties:

* `taskQueueId`: Identifies the task queue that this subscription addresses. Must correspond to a valid `id` of a `[task-queue]` element in the broker configuration
* `taskType`: Restricts the subscription to tasks of the given type. Corresponds to the value provided in the BPMN XML.

### Implementing a Task Handler

Implement the interface `org.camunda.tngp.client.task.TaskHandler` to create a task handler. `TaskHandler` is a functional interface, meaning that it can be implemented as a lambda. Implementations must be thread-safe.

Put the business logic for executing a task into the handler's `#handle` method. `#handle` is called whenever the client receives a task and takes an instance of `org.camunda.tngp.client.task.Task`. A `Task` instance provides access to the task's workflow context and methods to complete the task.

Example:

```java
public class SendInvoiceHandler implements TaskHandler
{

    @Override
    public void handle(Task task)
    {
        // actually do send invoice

        task.complete();
    }
}
```

### Closing a Subscription

```java
TaskSubscription subscription = ...;

subscription.close();
```

Once closed, the client no longer receives tasks for that subscription. The close operation blocks until all previously received tasks have been given to the handler.


## Standalone Task API

If you use task subscriptions, you most likely do not need most operations of the standalone API. It can be used to perform individual actions independently such as completing an arbitrary task instance.

### Complete Task Instances

```java
tasksClient.complete()
    .taskQueueId(0)
    .taskId(lockedTask.getId())
    .execute();
```

### Poll and Lock Task Instances

```java
LockedTasksBatch taskBatch = tasksClient.pollAndLock()
    .taskQueueId(0)
    .maxTasks(1)
    .taskType("print hello")
    .lockTime(10000L)
    .execute();
```

This method is an alternative to task subscriptions.

Limitations:

* Max tasks can only be 1
* Tasks with an expired lock time do not become lockable again
