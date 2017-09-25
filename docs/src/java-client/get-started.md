# Get Started with the Java client

In this tutorial, you will learn to use the Java client in a Java application to interact with Zeebe.

You will be guided through the following steps:

* [Set up a project](#set-up-a-project)
* [Model a workflow](#model-a-workflow)
* [Deploy a workflow](#deploy-a-workflow)
* [Create a workflow instance](#create-a-workflow-instance)
* [Open a topic subscription](#open-a-topic-subscription)
* [Work on a task](#work-on-a-task)
* [Work with data](#work-with-data)

> You can find the complete source code, including the BPMN diagrams, on [GitHub](https://github.com/zeebe-io/zeebe-get-started-java-client).

## Prerequisites

* Java 8
* [Zeebe distribution](../introduction/install.html)
* [Camunda Modeler](https://docs.camunda.org/manual/installation/camunda-modeler/)
* [Apache Maven](https://maven.apache.org/)

Start the Zeebe broker.

## Set up a project

First, we need a Maven project.
Create a new project using your IDE, or run the Maven command:

```
mvn archetype:generate -DgroupId=io.zeebe -DartifactId=zeebe-get-started-java-client -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
```

Add the Zeebe client library as dependency to the project's `pom.xml`:

```xml
<dependency>
  <groupId>io.zeebe</groupId>
  <artifactId>zeebe-client-java</artifactId>
  <version>${zeebe.version}</version>
</dependency>
```

Create a main class and add the following lines to bootstrap the Zeebe client:

```java
package io.zeebe;

import java.util.Properties;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;

public class Application
{
    public static void main(String[] args)
    {
        final Properties clientProperties = new Properties();
        // change the contact point if needed
        clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, "127.0.0.1:51015");

        final ZeebeClient client = ZeebeClient.create(clientProperties);

        client.connect();
        System.out.println("Connected.");

        // ...

        client.close();
        System.out.println("Closed.");
    }
}
```

Run the program.
If you use an IDE, you can just execute the main class.
Otherwise, you must build an executable JAR file with Maven and execute it.
(See the GitHub repository on how to do this.)

You should see the output:

```
Connected.

Closed.
```

## Model a workflow

Now, we need a first workflow which can then be deployed.
Later, we will extend the workflow with more functionality.

Open the Camunda Modeler and create a new BPMN diagram.
Add a start event and an end event to the diagram and connect the events.

![model-workflow-step-1](/java-client/order-process-simple.png)

Set the id (i.e., the BPMN process id) and mark the diagram as executable.
Save the diagram in the project's source folder.

## Deploy a workflow

Next, we want to deploy the modeled workflow to the broker.
The broker stores the workflow under its BPMN process id and assigns a version (i.e., the revision).

Add the following deploy command to the main class:

```java
package io.zeebe;

import io.zeebe.client.event.DeploymentEvent;

public class Application
{
    private static final String TOPIC = "default-topic";

    public static void main(String[] args)
    {
        // after the client is connected

        final DeploymentEvent deployment = client.workflows()
            .deploy(TOPIC)
            .resourceFromClasspath("order-process.bpmn")
            .execute();

        final int version = deployment.getDeployedWorkflows().get(0).getVersion();
        System.out.println("Workflow deployed. Version: " + version);

        // ...
    }
}
```

Run the program and verify that the workflow is deployed successfully.
You should see the output:

```
Workflow deployed. Version: 1
```

## Create a workflow instance

Finally, we are ready to create a first instance of the deployed workflow.
A workflow instance is created of a specific version of the workflow, which can be set on creation.

Add the following create command to the main class:

```java
package io.zeebe;

import io.zeebe.client.event.WorkflowInstanceEvent;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow is deployed

        final WorkflowInstanceEvent wfInstance = client.workflows()
            .create(TOPIC)
            .bpmnProcessId("order-process")
            .latestVersion()
            .execute();

        final long workflowInstanceKey = wfInstance.getWorkflowInstanceKey();

        System.out.println("Workflow instance created. Key: " + workflowInstanceKey);

        // ...
    }
}
```

Run the program and verify that the workflow instance is created. You should see the output:

```
Workflow instance created. Key: 4294986840
```

## Open a topic subscription

You did it! You want to see how the workflow instance is executed?

Unfortunately, we don't have a UI for Zeebe yet.

But we can use a [topic subscription] to monitor the workflow instance.
When the topic subscription is open, then we receive all events which are written during execution of the workflow instance.
The given handler is invoked for each received event.

Add the following lines to the main class to print all events:

```java
package io.zeebe;

import io.zeebe.client.event.TopicSubscription;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow instance is created

        final TopicSubscription topicSubscription = client.topics()
            .newSubscription(TOPIC)
            .name("app-monitoring")
            .startAtHeadOfTopic()
            .workflowInstanceEventHandler(event ->
            {
                System.out.println("> " + event);
            })
            .open();

        // waiting for the events

        topicSubscription.close();

        // ...
    }
}
```

Run the program. You should see the output:

```
> WorkflowInstanceEvent [state=CREATE_WORKFLOW_INSTANCE,
    workflowInstanceKey=-1,
    workflowKey=-1,
    bpmnProcessId=order-process,
    version=-1,
    activityId=null,
    payload=null]

> WorkflowInstanceEvent [state=WORKFLOW_INSTANCE_CREATED,
    workflowInstanceKey=4294986840,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=,
    payload=null]

> WorkflowInstanceEvent [state=START_EVENT_OCCURRED,
    workflowInstanceKey=4294986840,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=new-order-received,
    payload=null]

> WorkflowInstanceEvent [state=SEQUENCE_FLOW_TAKEN,
    workflowInstanceKey=4294986840,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=SequenceFlow_05suiqb,
    payload=null]

> WorkflowInstanceEvent [state=END_EVENT_OCCURRED,
    workflowInstanceKey=4294986840,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=order-shipped,
    payload=null]

> WorkflowInstanceEvent [state=WORKFLOW_INSTANCE_COMPLETED,
    workflowInstanceKey=4294986840,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=,
    payload=null]
```

Each of these events represents one step in the workflow instance life cycle.

## Work on a task

Now we want to do some work within your workflow.
First, add a few service tasks to the BPMN diagram and set the required attributes.
Then extend your main class and open a task subscription to process tasks which are created when the workflow instance reaches a service task.

Open the BPMN diagram in the Camunda Modeler.
Insert a few service tasks between the start and the end event.

![model-workflow-step-2](/java-client/order-process.png)

Switch to the XML view and add the Zeebe namespace to the root `definitions` element:

```xml
<bpmn:definitions xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
```

You need to set the type of each task, which identifies the nature of the work to be performed.

Add a `zeebe:taskDefinition` element to the service tasks extension elements.
Optionally, you can define parameters of the task by adding a `zeebe:taskHeaders` element.

```xml
<bpmn:serviceTask id="reserve-order-items" name="Reserve Order Items">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="reserveOrderItems" />
  	<zeebe:taskHeaders>
      <zeebe:header key="reservationTime" value="PT15M" />
    </zeebe:taskHeaders>
  </bpmn:extensionElements>
  <bpmn:incoming>SequenceFlow_05suiqb</bpmn:incoming>
  <bpmn:outgoing>SequenceFlow_17kmq07</bpmn:outgoing>
</bpmn:serviceTask>
```

Save the BPMN diagram and switch back to the main class.

Add the following lines to open a [task subscription] for the first tasks type:

```java
package io.zeebe;

import io.zeebe.client.task.TaskSubscription;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow instance is created

        final TaskSubscription taskSubscription = client.tasks()
            .newTaskSubscription(TOPIC)
            .taskType("reserveOrderItems")
            .lockOwner("stocker")
            .lockTime(Duration.ofMinutes(5))
            .handler((tasksClient, task) ->
            {
                final Map<String, Object> headers = task.getCustomHeaders();
                final String reservationTime = (String) headers.get("reservationTime");

                System.out.println("Reserved order items for " + reservationTime);

                // ...

                tasksClient
                    .complete(task)
                    .withoutPayload()
                    .execute();
            })
            .open();

        // waiting for the tasks

        taskSubscription.close();

        // ...
    }
}
```

Run the program and verify that the task is processed. You should see the output:

```
Reserved order items for PT15M
```

When you have a look at the topic subscriptions output, then you can see that the workflow instance moved from the first service task to the next one:

```
> WorkflowInstanceEvent [state=ACTIVITY_COMPLETED,
    workflowInstanceKey=4294986352,
    workflowKey=4294980048,
    bpmnProcessId=order-process,
    version=1,
    activityId=reserve-order-items,
    payload=null]

> WorkflowInstanceEvent [state=SEQUENCE_FLOW_TAKEN,
    workflowInstanceKey=4294986352,
    workflowKey=4294980048,
    bpmnProcessId=order-process,
    version=1,
    activityId=SequenceFlow_17kmq07,
    payload=null]

> WorkflowInstanceEvent [state=ACTIVITY_READY,
    workflowInstanceKey=4294986352,
    workflowKey=4294980048,
    bpmnProcessId=order-process,
    version=1,
    activityId=process-payment,
    payload=null]
```

## Work with data

Usually, a workflow is more than just tasks, there is also data flow.
The tasks need data as input and in order to produce data.

In Zeebe, the data is represented as a JSON document.
When you create a workflow instance, then you can pass the data as payload.
Within the workflow, you can use input and output mappings on tasks to control the data flow.

In our example, we want to create a workflow instance with the following data:

```json
{
  "orderId": 31243,
  "orderStatus": "NEW",
  "orderItems": [435, 182, 376]
}
```

The first task should take `orderItems` as input and modify the `orderStatus` as result.

Open the BPMN diagram and add to following input-output-mapping to the first task:

```xml
<bpmn:serviceTask id="reserve-order-items" name="Reserve Order Items">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="reserveOrderItems" />
  	<zeebe:taskHeaders>
      <zeebe:header key="reservationTime" value="PT15M" />
    </zeebe:taskHeaders>
    <zeebe:ioMapping>
      <zeebe:input source="$.orderItems" target="$.orderItems" />
      <zeebe:output source="$.orderStatus" target="$.orderStatus" />
    </zeebe:ioMapping>
  </bpmn:extensionElements>
  <bpmn:incoming>SequenceFlow_05suiqb</bpmn:incoming>
  <bpmn:outgoing>SequenceFlow_17kmq07</bpmn:outgoing>
</bpmn:serviceTask>
```

Save the BPMN diagram and switch back to the main class.

Modify the create command and pass the data as payload.
Also, modify the task subscription to read the tasks payload and complete the task with payload.

```java
package io.zeebe;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow is deployed

        final WorkflowInstanceEvent wfInstance = client.workflows()
            .create(TOPIC)
            .bpmnProcessId("order-process")
            .latestVersion()
            .payload(data)
            .execute();

        // ...

        final TaskSubscription taskSubscription = client.tasks()
            .newTaskSubscription(TOPIC)
            .taskType("reserveOrderItems")
            .lockOwner("stocker")
            .lockTime(Duration.ofMinutes(5))
            .handler((tasksClient, task) ->
            {
                final Map<String, Object> headers = task.getCustomHeaders();
                final String reservationTime = (String) headers.get("reservationTime");

                final String orderItems = task.getPayload();

                System.out.println("Reserved " + orderItems + "for" + reservationTime);

                // ...

                tasksClient
                     .complete(task)
                     .payload("{ \"orderStatus\": \"RESERVED\" }")
                     .execute();
            })
            .open();

        // ...
    }
}
```

Run the program and verify that the payload is mapped into the task. You should see the output:

```
Reserved {"orderItems":[435,182,376]} for PT15M
```

When we have a look at the topic subscriptions output, then we can see how the payload is mapped between the activities:

```
> WorkflowInstanceEvent [state=ACTIVITY_READY,
    workflowInstanceKey=4294986992,
    workflowKey=4294980472,
    bpmnProcessId=order-process,
    version=1,
    activityId=reserve-order-items,
    payload={"orderId":31243,"orderStatus":"NEW","orderItems":[435,182,376]}]

> WorkflowInstanceEvent [state=ACTIVITY_ACTIVATED,
    workflowInstanceKey=4294986992,
    workflowKey=4294980472,
    bpmnProcessId=order-process,
    version=1,
    activityId=reserve-order-items,
    payload={"orderItems":[435,182,376]}]

> WorkflowInstanceEvent [state=ACTIVITY_COMPLETING,
    workflowInstanceKey=4294986992,
    workflowKey=4294980472,
    bpmnProcessId=order-process,
    version=1,
    activityId=reserve-order-items,
    payload={"orderStatus":"RESERVED"}]

> WorkflowInstanceEvent [state=ACTIVITY_COMPLETED,
    workflowInstanceKey=4294986992,
    workflowKey=4294980472,
    bpmnProcessId=order-process,
    version=1,
    activityId=reserve-order-items,
    payload={"orderId":31243,"orderItems":[435,182,376],"orderStatus":"RESERVED"}]
```

## What's next?

Hurray! You finished this tutorial and learned the basic usage of the Java client.

Next steps:
* Learn more about the [concepts behind Zeebe](/basics/README.html)
* Learn more about [BPMN workflows](/bpmn-workflows/README.html)
* Take a deeper look into the [Java client](java-client/README.html)

[topic subscription]: ../basics/topics-and-logs.html
[task subscription]: ../basics/task-workers.html
