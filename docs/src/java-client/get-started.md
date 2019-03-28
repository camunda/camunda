# Get Started with the Java client

In this tutorial, you will learn to use the Java client in a Java application to interact with Zeebe.

You will be guided through the following steps:

* [Set up a project](#set-up-a-project)
* [Model a workflow](#model-a-workflow)
* [Deploy a workflow](#deploy-a-workflow)
* [Create a workflow instance](#create-a-workflow-instance)
* [Work on a job](#work-on-a-job)
* [Work with data](#work-with-data)

> You can find the complete source code, including the BPMN diagrams, on [GitHub](https://github.com/zeebe-io/zeebe-get-started-java-client).

## Prerequisites

* Java 8
* [Apache Maven](https://maven.apache.org/)
* [Zeebe distribution](../introduction/install.html)
* [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases)
* [Zeebe Monitor](https://github.com/zeebe-io/zeebe-simple-monitor/releases)

Before you begin to setup your project please start the broker, i.e. by running the start up script
`bin/broker` or `bin/broker.bat` in the distribution. Per default the broker is binding to the
address `localhost:26500`, which is used as contact point in this guide. In case your broker is
available under another address please adjust the broker contact point when building the client.

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
        final ZeebeClient client = ZeebeClient.newClientBuilder()
            // change the contact point if needed
            .brokerContactPoint("127.0.0.1:26500")
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

        final DeploymentEvent deployment = client.newDeployCommand()
            .addResourceFromClasspath("order-process.bpmn")
            .send()
            .join();

        final int version = deployment.getWorkflows().get(0).getVersion();
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

        final WorkflowInstanceEvent wfInstance = client.newCreateInstanceCommand()
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
Workflow instance created. Key: 6
```

You did it! You want to see how the workflow instance is executed?

Start the Zeebe Monitor using `java -jar zeebe-simple-monitor-app-*.jar`.

Open a web browser and go to <http://localhost:8080/>.

Here, you see the current state of the workflow instance.
![zeebe-monitor-step-1](/java-client/java-get-started-monitor-1.gif)

## Work on a job

Now we want to do some work within your workflow.
First, add a few service jobs to the BPMN diagram and set the required attributes.
Then extend your main class and create a job worker to process jobs which are created when the workflow instance reaches a service task.

Open the BPMN diagram in the Zeebe Modeler.
Insert a few service tasks between the start and the end event.

![model-workflow-step-2](/java-client/order-process.png)

You need to set the type of each task, which identifies the nature of the work to be performed.
Set the type of the first task to 'payment-service'.

Save the BPMN diagram and switch back to the main class.

Add the following lines to create a job worker for the first jobs type:

```java
package io.zeebe;

import io.zeebe.client.api.subscription.JobWorker;

public class Application
{
    public static void main(String[] args)
    {
        // after the workflow instance is created

        final JobWorker jobWorker = client.newWorker()
            .jobType("payment-service")
            .handler((jobClient, job) ->
            {
                System.out.println("Collect money");

                // ...

                jobClient.newCompleteCommand(job.getKey())
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
Collect money
```

When you have a look at the Zeebe Monitor, then you can see that the workflow instance moved from the first service task to the next one:

![zeebe-monitor-step-2](/java-client/java-get-started-monitor-2.gif)

## Work with data

Usually, a workflow is more than just tasks, there is also a data flow. The worker gets the data from the workflow instance to do its work and send the result back to the workflow instance.

In Zeebe, the data is stored as key-value-pairs in form of variables. Variables can be set when the workflow instance is created. Within the workflow, variables can be read and modified by workers.

In our example, we want to create a workflow instance with the following variables:

```json
"orderId": 31243
"orderItems": [435, 182, 376]
```

The first task should read `orderId` as input and return `totalPrice` as result.

Modify the workflow instance create command and pass the data as variables. Also, modify the job worker to read the job variables and complete the job with a result.

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

        final WorkflowInstanceEvent wfInstance = client.newCreateInstanceCommand()
            .bpmnProcessId("order-process")
            .latestVersion()
            .variables(data)
            .send()
            .join();

        // ...

        final JobWorker jobWorker = client.newWorker()
            .jobType("payment-service")
            .handler((jobClient, job) ->
            {
                final Map<String, Object> variables = job.getVariablesAsMap();

                System.out.println("Process order: " + variables.get("orderId"));
                System.out.println("Collect money");

                // ...

                final Map<String, Object> result = new HashMap<>();
                result.put("totalPrice", 46.50);

                jobClient.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send()
                    .join();
            })
            .fetchVariables("orderId")
            .open();

        // ...
    }
}
```

Run the program and verify that the variable is read. You should see the output:

```
Process order: 31243
Collect money
```

When we have a look at the Zeebe Monitor, then we can see that the variable `totalPrice` is set:

![zeebe-monitor-step-3](/java-client/java-get-started-monitor-3.gif)

## What's next?

Hurray! You finished this tutorial and learned the basic usage of the Java client.

Next steps:
* Learn more about the [concepts behind Zeebe](/basics/README.html)
* Learn more about [BPMN workflows](/bpmn-workflows/README.html)
* Take a deeper look into the [Java client](java-client/README.html)

[job worker]: ../basics/job-workers.html
