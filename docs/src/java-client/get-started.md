# Get Started with the Java client

In this tutorial, you will learn to use the Java client in a Java application to interact with Zeebe.

You will be guided through the following steps:

* [Set up a project](#set-up-a-project)
* [Model a workflow](#model-a-workflow)
* [Deploy a workflow](#deploy-a-workflow)
* [Create a workflow instance](#create-a-workflow-instance)
* [Work on a job](#work-on-a-job)
* [Work with data](#work-with-data)
* [Open a topic subscription](#open-a-topic-subscription)

> You can find the complete source code, including the BPMN diagrams, on [GitHub](https://github.com/zeebe-io/zeebe-get-started-java-client).

## Prerequisites

* Java 8
* [Apache Maven](https://maven.apache.org/)
* [Zeebe distribution](../introduction/install.html)
* [Zeebe Command Line Client zbctl](https://github.com/zeebe-io/zbctl)
* [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases)
* [Zeebe Monitor](https://github.com/zeebe-io/zeebe-simple-monitor/releases)

Now, start the Zeebe broker. This guide uses the `default-topic`, which is created
when the broker is started.

In case you want to create another topic you can use `zbctl` from the `bin` folder.
Create the topic with zbctl by executing the following command on the command line:

```
zbctl create topic my-topic --partitions 1
```

You should see the output:

```
{
  "Name": "my-topic",
  "Partitions": 1,
  "ReplicationFactor": 1
}
```

**Note:** You can find the `zbctl` binary in the `bin/` folder of the Zeebe
distribution. On Windows systems the executable is called `zbctl.exe` and on
MacOS `zbctl.darwin`.

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

        final ZeebeClient client = ZeebeClient.newClientBuilder()
            .withProperties(clientProperties)
            .build();

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

import io.zeebe.client.api.events.DeploymentEvent;

public class Application
{
    public static void main(String[] args)
    {
        // after the client is connected

        final DeploymentEvent deployment = client.topicClient().workflowClient()
            .newDeployCommand()
            .addResourceFromClasspath("order-process.bpmn")
            .send()
            .join();

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

import io.zeebe.client.api.events.WorkflowInstanceEvent;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow is deployed

        final WorkflowInstanceEvent wfInstance = client.topicClient().workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("order-process")
            .latestVersion()
            .send()
            .join();

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
Here, you see the current state of the workflow instance which includes active jobs, completed activities, the payload and open incidents.

![zeebe-monitor-step-1](/java-client/zeebe-monitor-1.png)

## Work on a job

Now we want to do some work within your workflow.
First, add a few service jobs to the BPMN diagram and set the required attributes.
Then extend your main class and create a job worker to process jobs which are created when the workflow instance reaches a service task.

Open the BPMN diagram in the Zeebe Modeler.
Insert a few service tasks between the start and the end event.

![model-workflow-step-2](/java-client/order-process.png)

You need to set the type of each task, which identifies the nature of the work to be performed.
Set the type of the first task to 'payment-service'.

Optionally, you can define parameters of the task by adding headers.
Add the header `method = VISA` to the first task.

Save the BPMN diagram and switch back to the main class.

Add the following lines to create a [job worker][] for the first jobs type:

```java
package io.zeebe;

import io.zeebe.client.api.subscription.JobWorker;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow instance is created

        final JobWorker jobWorker = client.topicClient().jobClient()
            .newWorker()
            .jobType("payment-service")
            .handler((jobClient, job) ->
            {
                final Map<String, Object> headers = job.getCustomHeaders();
                final String method = (String) headers.get("method");

                System.out.println("Collect money using payment method: " + method);

                // ...

                jobClient.newCompleteCommand(job)
                    .send()
                    .join();
            })
            .open();

        // waiting for the jobs

        jobWorker.close();

        // ...
    }
}
```

Run the program and verify that the job is processed. You should see the output:

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
Also, modify the job worker to read the jobs payload and complete the job with payload.

```java
package io.zeebe;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow is deployed
        
        final Map<String, Object> data = new HashMap<>();
        data.put("orderId", 31243);
        data.put("orderItems", Arrays.asList(435, 182, 376));

        final WorkflowInstanceEvent wfInstance = client.topicClient().workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("order-process")
            .latestVersion()
            .payload(data)
            .send()
            .join();

        // ...

        final JobWorker jobWorker = client.topicClient().jobClient()
            .newWorker()
            .jobType("payment-service")
            .handler((jobClient, job) ->
            {
                final Map<String, Object> headers = job.getCustomHeaders();
                final String method = (String) headers.get("method");

                final Map<String, Object> payload = job.getPayloadAsMap();

                System.out.println("Process order: " + payload.get("orderId"));
                System.out.println("Collect money using payment method: " + method);

                // ...

                payload.put("totalPrice", 46.50);

                jobClient.newCompleteCommand(job)
                    .payload(payload)
                    .send()
                    .join();
            })
            .open();

        // ...
    }
}
```

Run the program and verify that the payload is mapped into the job. You should see the output:

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

import io.zeebe.client.api.subscription.TopicSubscription;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow instance is created

        final TopicSubscription topicSubscription = client.topicClient()
            .newSubscription()
            .name("app-monitoring")
            .workflowInstanceEventHandler(e -> System.out.println(e.toJson()))
            .startAtHeadOfTopic()
            .open();

        // waiting for the events

        topicSubscription.close();

        // ...
    }
}
```

Run the program. You should see the output:

```
{
  "metadata": {
    "intent": "CREATED",
    "valueType": "WORKFLOW_INSTANCE",
    "recordType": "EVENT",
    "topicName": "default-topic",
    "partitionId": 1,
    "key": 4294967400,
    "position": 4294967616,
    "timestamp": "2018-05-30T11:40:40.599Z"
  },
  "bpmnProcessId": "order-process",
  "version": 1,
  "workflowKey": 1,
  "workflowInstanceKey": 4294967400,
  "activityId": "",
  "payload": {
    "orderId": 31243,
    "orderItems": [
      435,
      182,
      376
    ]
  }
}

{
  "metadata": {
    "intent": "START_EVENT_OCCURRED",
    "valueType": "WORKFLOW_INSTANCE",
    "recordType": "EVENT",
    "topicName": "default-topic",
    "partitionId": 1,
    "key": 4294967856,
    "position": 4294967856,
    "timestamp": "2018-05-30T11:40:40.599Z"
  },
  "bpmnProcessId": "order-process",
  "version": 1,
  "workflowKey": 1,
  "workflowInstanceKey": 4294967400,
  "activityId": "order-placed",
  "payload": {
    "orderId": 31243,
    "orderItems": [
      435,
      182,
      376
    ]
  }
}

{
  "metadata": {
    "intent": "SEQUENCE_FLOW_TAKEN",
    "valueType": "WORKFLOW_INSTANCE",
    "recordType": "EVENT",
    "topicName": "default-topic",
    "partitionId": 1,
    "key": 4294968128,
    "position": 4294968128,
    "timestamp": "2018-05-30T11:40:40.621Z"
  },
  "bpmnProcessId": "order-process",
  "version": 1,
  "workflowKey": 1,
  "workflowInstanceKey": 4294967400,
  "activityId": "SequenceFlow_18tqka5",
  "payload": {
    "orderId": 31243,
    "orderItems": [
      435,
      182,
      376
    ]
  }
}
...
```

## What's next?

Hurray! You finished this tutorial and learned the basic usage of the Java client.

Next steps:
* Learn more about the [concepts behind Zeebe](/basics/README.html)
* Learn more about [BPMN workflows](/bpmn-workflows/README.html)
* Take a deeper look into the [Java client](java-client/README.html)

[topic subscription]: ../basics/topics-and-logs.html
[job worker]: ../basics/job-workers.html
