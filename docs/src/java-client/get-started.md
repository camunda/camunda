# Get Started with the Java client

In this tutorial you will how to use the Java client in a Java application to interact with Zeebe.

You will be guided through the following steps:

* [Setup a project](#setup-a-project)
* [Model a workflow](#model-a-workflow)
* [Deploy a workflow](#deploy-a-workflow)
* [Create a workflow instance](#create-a-workflow-instance)
* [Open a topic subscription](#open-a-topic-subscription)
* [Work on a task](#work-on-a-task)
* [Work with data](#work-with-data)

> You can find the complete source code including the BPMN diagrams on [GitHub](https://github.com/camunda-zeebe/zeebe-get-started-java-client).

## Prerequisites

* Java 8
* [Zeebe distribution](../introduction/install.md)
* [Camunda Modeler](https://docs.camunda.org/manual/installation/camunda-modeler/)
* [Apache Maven](https://maven.apache.org/)

Then, start the Zeebe broker.

## Setup a project

First, we need a Maven project.
Create a new project using your IDE or run the Maven command:

```
mvn archetype:generate -DgroupId=io.zeebe -DartifactId=zeebe-get-started-java-client
```

Then, add the Zeebe client library as dependency to the project's `pom.xml`:

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

Run the program using your IDE or the Maven command:

```
mvn clean package

java -cp target/zeebe-t-started-java-client-0.1.0-SNAPSHOT-jar-with-dependencies.jar io.zeebe.Application
```

<!-- TODO provide an easy way to run the program -->

You should see the output:

```
Connected.

Closed.
```

## Model a workflow

Now, we need a first workflow which can be deployed afterwards.
Later, we will extend the workflow with more functionality.

Open the Camunda Modeler and create a new BPMN diagram.
Add a start event and an end event to the diagram and connect the events.

![model-workflow-step-1](/java-client/model-workflow-1.png)

<!-- TODO adjust the size of the image -->

Set the id (i.e., the BPMN process id) and mark the diagram as executable.
Save the diagram in the project's source folder.

## Deploy a workflow

Next, we want to deploy the modeled workflow to the broker.
The broker stores the workflow under its BPMN process id and assign a version (i.e. the revision).

Add the following deploy command to the main class:

```java
package io.zeebe;

import io.zeebe.client.workflow.cmd.DeploymentResult;

public class Application
{
    private static final String TOPIC = "default-topic";
    private static final int PARTITION_ID = 0;

    public static void main(String[] args)
    {
        // after the client is connected

        final DeploymentResult result = client.workflowTopic(TOPIC, PARTITION_ID)
            .deploy()
            .resourceFromClasspath("order-process.bpmn")
            .execute();

        if (result.isDeployed())
        {
            final int version = result.getDeployedWorkflows().get(0).getVersion();

            System.out.println("Workflow deployed. Version: " + version);
        }
        else
        {
            final String errorMessage = result.getErrorMessage();

            System.out.println("Failed to deploy workflow: " + errorMessage);
        }

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

Finally, we are ready to create our first instance of the deployed workflow.
A workflow instance is created of a specific version of the workflow which can be set on creation.

Add the following create command to the main class:

```java
package io.zeebe;

import io.zeebe.client.workflow.cmd.WorkflowInstance;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow is deployed

        final WorkflowInstance wfInstance = client.workflowTopic(TOPIC, PARTITION_ID)
            .create()
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
Workflow instance created. Key: 4294986992
```

## Open a topic subscription

You did it! You want to see how the workflow instance is executed?

Unfortunately, we have no UI for Zeebe, yet.

But we can use a topic subscription for the monitoring of the workflow instance.
When the topic subscription is open then we receive all events which are written while the execution of the workflow instance.
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

        final TopicSubscription topicSubscription = client.topic(TOPIC, PARTITION_ID)
            .newSubscription()
            .name("app-monitoring")
            .startAtHeadOfTopic()
            .workflowInstanceEventHandler((metadata, event ) ->
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
> WorkflowInstanceEvent [eventType=CREATE_WORKFLOW_INSTANCE,
    workflowInstanceKey=-1,
    workflowKey=-1,
    bpmnProcessId=order-process,
    version=-1,
    activityId=null,
    payload=null]

> WorkflowInstanceEvent [eventType=WORKFLOW_INSTANCE_CREATED,
    workflowInstanceKey=4294974384,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=,
    payload=null]

> WorkflowInstanceEvent [eventType=START_EVENT_OCCURRED,
    workflowInstanceKey=4294974384,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=new-order-received,
    payload=null]

> WorkflowInstanceEvent [eventType=SEQUENCE_FLOW_TAKEN,
    workflowInstanceKey=4294974384,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=SequenceFlow_05suiqb,
    payload=null]

> WorkflowInstanceEvent [eventType=END_EVENT_OCCURRED,
    workflowInstanceKey=4294974384,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=order-shipped,
    payload=null]

> WorkflowInstanceEvent [eventType=WORKFLOW_INSTANCE_COMPLETED,
    workflowInstanceKey=4294974384,
    workflowKey=4294972072,
    bpmnProcessId=order-process,
    version=1,
    activityId=,
    payload=null]
```

Each of these events represents one step in the workflow instance life cycle.

## Work on a task

Now, we want to do some work within your workflow.
First, we add a few service tasks to the BPMN diagram and set the required attributes.
Then, we extend your main class and open a task subscription to process tasks which are created when the workflow instance reaches a service task.    

Open the BPMN diagram in the Camunda Modeler.
Insert a few service tasks between the start and the end event.

![model-workflow-step-2](/java-client/model-workflow-2.png)

Switch to the XML view and add the Zeebe namespace to the root `definitions` element:

```xml
<bpmn:definitions xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
```

Then you need to set the type of each task which identifies the nature of the work to be performed.

Add a `zeebe:taskDefinition` element to the service task's extension elements.
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

Add the following lines to open a task subscription for the first task's type:

```java
package io.zeebe;

import io.zeebe.client.task.TaskSubscription;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow instance is created

        final TaskSubscription taskSubscription = client.taskTopic(TOPIC, PARTITION_ID)
            .newTaskSubscription()
            .taskType("reserveOrderItems")
            .lockOwner("stocker")
            .lockTime(Duration.ofMinutes(5))
            .handler(task ->
            {
                final Map<String, Object> headers = task.getHeaders();
                final String reservationTime = (String) headers.get("reservationTime");

                System.out.println("Reserved order items for " + reservationTime);

                // ...

                task.complete();
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

When you have a look at the topic subscription's output then you can see that the workflow instance moved from the first service task to the next one:

```
> WorkflowInstanceEvent [eventType=ACTIVITY_COMPLETED,
    workflowInstanceKey=4294986352,
    workflowKey=4294980048,
    bpmnProcessId=order-process,
    version=1,
    activityId=reserve-order-items,
    payload=null]

> WorkflowInstanceEvent [eventType=SEQUENCE_FLOW_TAKEN,
    workflowInstanceKey=4294986352,
    workflowKey=4294980048,
    bpmnProcessId=order-process,
    version=1,
    activityId=SequenceFlow_17kmq07,
    payload=null]

> WorkflowInstanceEvent [eventType=ACTIVITY_READY,
    workflowInstanceKey=4294986352,
    workflowKey=4294980048,
    bpmnProcessId=order-process,
    version=1,
    activityId=process-payment,
    payload=null]
```

## Work with data

Usually, a workflow is more than just tasks, there is also a data flow.
The tasks need data as input and produce data.

In Zeebe, the data is represented as a JSON document.
When you create a workflow instance then you can pass the data as payload.
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
Also, modify the task subscription to read the task's payload and complete the task with payload.

```java
package io.zeebe;

import io.zeebe.client.task.TaskSubscription;

public class Application
{  
    public static void main(String[] args)
    {
        // after the workflow is deployed

        final WorkflowInstance wfInstance = client.workflowTopic(TOPIC, PARTITION_ID)
            .create()
            .bpmnProcessId("order-process")
            .latestVersion()
            .payload(data)
            .execute();

        // ...    

        final TaskSubscription taskSubscription = client.taskTopic(TOPIC, PARTITION_ID)
            .newTaskSubscription()
            .taskType("reserveOrderItems")
            .lockOwner("stocker")
            .lockTime(Duration.ofMinutes(5))
            .handler(task ->
            {
                final Map<String, Object> headers = task.getHeaders();
                final String reservationTime = (String) headers.get("reservationTime");

                final String orderItems = task.getPayload();

                System.out.println("Reserved " + orderItems + "for" + reservationTime);

                // ...

                task.complete("{ \"orderStatus\": \"RESERVED\" }");
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

When we have a look at the topic subscription's output then we can see how the payload is mapped between the activities:

```
> WorkflowInstanceEvent [eventType=ACTIVITY_READY,
    workflowInstanceKey=4294986992,
    workflowKey=4294980472,
    bpmnProcessId=order-process,
    version=1,
    activityId=reserve-order-items,
    payload={"orderId":31243,"orderStatus":"NEW","orderItems":[435,182,376]}]

> WorkflowInstanceEvent [eventType=ACTIVITY_ACTIVATED,
    workflowInstanceKey=4294986992,
    workflowKey=4294980472,
    bpmnProcessId=order-process,
    version=1,
    activityId=reserve-order-items,
    payload={"orderItems":[435,182,376]}]

> WorkflowInstanceEvent [eventType=ACTIVITY_COMPLETING,
    workflowInstanceKey=4294986992,
    workflowKey=4294980472,
    bpmnProcessId=order-process,
    version=1,
    activityId=reserve-order-items,
    payload={"orderStatus":"RESERVED"}]

> WorkflowInstanceEvent [eventType=ACTIVITY_COMPLETED,
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
* learn more about the [concepts behind Zeebe](/basics/README.md)
* learn more about [BPMN workflows](/bpmn-workflows/README.md)
* take a deeper look into the [Java client](java-client/README.md)  
