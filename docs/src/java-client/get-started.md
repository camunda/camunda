# Get Started with the Java client

In this tutorial, you will learn to use the Java client in a Java application to interact with Zeebe.

You will be guided through the following steps:

* [Set up a project](#set-up-a-project)
* [Model a workflow](#model-a-workflow)
* [Deploy a workflow](#deploy-a-workflow)
* [Create a workflow instance](#create-a-workflow-instance)
* [Work on a task](#work-on-a-task)
* [Work with data](#work-with-data)
* [Open a topic subscription](#open-a-topic-subscription)

> You can find the complete source code, including the BPMN diagrams, on [GitHub](https://github.com/zeebe-io/zeebe-get-started-java-client).

## Prerequisites

* Java 8
* [Apache Maven](https://maven.apache.org/)
* [Zeebe distribution](../introduction/install.html)
* [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases)
* [Zeebe Monitor](https://github.com/zeebe-io/zeebe-simple-monitor/releases)

Now, start the Zeebe broker.

## Set up a project

First, we need a Maven project.
Create a new project using your IDE, or run the Maven command:

```
mvn archetype:generate
    -DgroupId=io.zeebe
    -DartifactId=zeebe-get-started-java-client
    -DarchetypeArtifactId=maven-archetype-quickstart
    -DinteractiveMode=false
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

Open the Zeebe Modeler and create a new BPMN diagram.
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
Workflow instance created. Key: 4294974008
```

You did it! You want to see how the workflow instance is executed?

Start the Zeebe Monitor using `java -jar zeebe-simple-monitor.jar`.

Open a web browser and go to <http://localhost:8080/>.

Connect to the broker and switch to the workflow instances view.
Here, you see the current state of the workflow instance which includes active tasks, completed activities, the payload and open incidents.  

![zeebe-monitor-step-1](/java-client/zeebe-monitor-1.png)

## Work on a task

Now we want to do some work within your workflow.
First, add a few service tasks to the BPMN diagram and set the required attributes.
Then extend your main class and open a task subscription to process tasks which are created when the workflow instance reaches a service task.

Open the BPMN diagram in the Zeebe Modeler.
Insert a few service tasks between the start and the end event.

![model-workflow-step-2](/java-client/order-process.png)

You need to set the type of each task, which identifies the nature of the work to be performed.
Set the type of the first task to 'payment-service'.

Optionally, you can define parameters of the task by adding headers.
Add the header `method = VISA` to the first task.

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
            .taskType("payment-service")
            .lockOwner("sample-app")
            .lockTime(Duration.ofMinutes(5))
            .handler((tasksClient, task) ->
            {
                final Map<String, Object> headers = task.getCustomHeaders();
                final String method = (String) headers.get("method");

                System.out.println("Collect money using payment method: " + method);

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
Collect money using payment method: VISA
```

When you have a look at the Zeebe Monitor, then you can see that the workflow instance moved from the first service task to the next one:

![zeebe-monitor-step-2](/java-client/zeebe-monitor-2.png)

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
  "orderItems": [435, 182, 376]
}
```

The first task should take `orderId` as input and return `totalPrice` as result.

Open the BPMN diagram and switch to the input-output-mappings of the first task.
Add the input mapping `$.orderId : $.orderId` and the output mapping `$.totalPrice : $.totalPrice`.

Save the BPMN diagram and go back to the main class.

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
            .taskType("payment-service")
            .lockOwner("sample-app")
            .lockTime(Duration.ofMinutes(5))
            .handler((tasksClient, task) ->
            {
                final Map<String, Object> headers = task.getCustomHeaders();
                final String method = (String) headers.get("method");

                final String orderId = task.getPayload();

                System.out.println("Process order: " + orderId);
                System.out.println("Collect money using payment method: " + method);

                // ...

                tasksClient
                     .complete(task)
                     .payload("{ \"totalPrice\": 46.50 }")
                     .execute();
            })
            .open();

        // ...
    }
}
```

Run the program and verify that the payload is mapped into the task. You should see the output:

```
Process order: {"orderId":31243}
Collect money using payment method: VISA
```

When we have a look at the Zeebe Monitor, then we can see how the payload is modified after the activity:

![zeebe-monitor-step-3](/java-client/zeebe-monitor-3.png)

## Open a topic subscription

The Zeebe Monitor consume the events of the broker to build the monitoring.
You can see all received events in the log view.
In order to build something similar for our application, we open a [topic subscription] and print all workflow instance events.

When the topic subscription is open, then we receive all events which are written during execution of the workflow instance.
The given handler is invoked for each received event.

Add the following lines to the main class:

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
    activityId=order-placed,
    payload=null]

...
```

## What's next?

Hurray! You finished this tutorial and learned the basic usage of the Java client.

Next steps:
* Learn more about the [concepts behind Zeebe](/basics/README.html)
* Learn more about [BPMN workflows](/bpmn-workflows/README.html)
* Take a deeper look into the [Java client](java-client/README.html)

[topic subscription]: ../basics/topics-and-logs.html
[task subscription]: ../basics/task-workers.html
