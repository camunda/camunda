# Get Started with the Go client

In this tutorial, you will learn to use the Go client in a Go application to interact with Zeebe.

You will be guided through the following steps:

* [Set up a project](#set-up-a-project)
* [Model a workflow](#model-a-workflow)
* [Deploy a workflow](#deploy-a-workflow)
* [Create a workflow instance](#create-a-workflow-instance)
* [Work on a task](#work-on-a-task)
* [Open a topic subscription](#open-a-topic-subscription)


> You can find the complete source code, on [GitHub](https://github.com/zeebe-io/zeebe-get-started-go-client).


## Prerequisites

* Go v1.11+ environment installed
* [Zeebe distribution](../introduction/install.html)
* [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases)
* [Zeebe Monitor](https://github.com/zeebe-io/zeebe-simple-monitor/releases)


Before you begin to setup your project please start the broker, i.e. by running the start up script
`bin/broker` or `bin/broker.bat` in the distribution. Per default the broker is binding to the
address `localhost:26500`, which is used as contact point in this guide. In case your broker is
available under another address please adjust the broker contact point when building the client.

## Set up a project

First, we need a new Go project.
Create a new project using your IDE, or create new Go module with:

```
mkdir -p $GOPATH/src/github.com/{{your username}}/zb-example
cd $GOPATH/src/github.com/{{your username}}/zb-example
```

Install Zeebe Go client library:

```
go get github.com/zeebe-io/zeebe/clients/go
```

Create a main.go file inside the module and add the following lines to bootstrap the Zeebe client:

```go
package main

import (
	"fmt"
	"github.com/zeebe-io/zeebe/clients/go/zbc"
	"github.com/zeebe-io/zeebe/clients/go/pb"
)

const BrokerAddr = "0.0.0.0:26500"

func main() {
	zbClient, err := zbc.NewZBClient(BrokerAddr)
	if err != nil {
		panic(err)
	}

	topology, err := zbClient.NewTopologyCommand().Send()
	if err != nil {
		panic(err)
	}

	for _, broker := range topology.Brokers {
		fmt.Println("Broker", broker.Host, ":", broker.Port)
		for _, partition := range broker.Partitions {
			fmt.Println("  Partition", partition.PartitionId, ":", roleToString(partition.Role))
		}
	}
}

func roleToString(role pb.Partition_PartitionBrokerRole) string {
	switch role {
	case pb.Partition_LEADER:
		return "Leader"
	case pb.Partition_FOLLOWER:
		return "Follower"
	default:
		return "Unknown"
	}
}
```

Run the program.

```bash
go run main.go
```
You should see similar output:

```
Broker 0.0.0.0 : 26501
  Partition 0 : Leader
```

## Model a workflow

Now, we need a first workflow which can then be deployed.
Later, we will extend the workflow with more functionality.

Open the Zeebe Modeler and create a new BPMN diagram.
Add a start event and an end event to the diagram and connect the events.

![model-workflow-step-1](/go-client/order-process-simple.png)

Set the id to `order-process` (i.e., the BPMN process id) and mark the diagram
as executable.  Save the diagram in the project's source folder.


## Deploy a workflow

Next, we want to deploy the modeled workflow to the broker.
The broker stores the workflow under its BPMN process id and assigns a version (i.e., the revision).

```go
package main

import (
	"fmt"
	"github.com/zeebe-io/zeebe/clients/go/zbc"
)

const brokerAddr = "0.0.0.0:26500"

func main() {
	zbClient, err := zbc.NewZBClient(brokerAddr)
	if err != nil {
		panic(err)
	}

	response, err := zbClient.NewDeployWorkflowCommand().AddResourceFile("order-process.bpmn").Send()
	if err != nil {
		panic(err)
	}

	fmt.Println(response.String())
}
```

Run the program and verify that the workflow is deployed successfully.
You should see similar the output:

```
key:1 workflows:<bpmnProcessId:"order-process" version:1 workflowKey:1 resourceName:"order-process.bpmn" >
```

## Create a workflow instance

Finally, we are ready to create a first instance of the deployed workflow.  A
workflow instance is created of a specific version of the workflow, which can
be set on creation.

```go
package main

import (
	"fmt"
	"github.com/zeebe-io/zeebe/clients/go/zbc"
)

const brokerAddr = "0.0.0.0:26500"

func main() {
	client, err := zbc.NewZBClient(brokerAddr)
	if err != nil {
		panic(err)
	}

	// After the workflow is deployed.
	payload := make(map[string]interface{})
	payload["orderId"] = "31243"

	request, err := client.NewCreateInstanceCommand().BPMNProcessId("order-process").LatestVersion().VariablesFromMap(payload)
	if err != nil {
		panic(err)
	}

	msg, err := request.Send()
	if err != nil {
		panic(err)
	}

	fmt.Println(msg.String())
}
```

Run the program and verify that the workflow instance is created. You should see the output:

```
workflowKey:1 bpmnProcessId:"order-process" version:1 workflowInstanceKey:6
```

You did it! You want to see how the workflow instance is executed?

Start the Zeebe Monitor using `java -jar zeebe-simple-monitor-app-*.jar`.

Open a web browser and go to <http://localhost:8080/>.

Here, you see the current state of the workflow instance.
![zeebe-monitor-step-1](/java-client/java-get-started-monitor-1.gif)


## Work on a task

Now we want to do some work within your workflow.  First, add a few service
tasks to the BPMN diagram and set the required attributes.  Then extend your
`main.go` file and activate a job which are created when the workflow instance
reaches a service task.

Open the BPMN diagram in the Zeebe Modeler.
Insert a few service tasks between the start and the end event.

![model-workflow-step-2](/go-client/order-process.png)

You need to set the type of each task, which identifies the nature of the work to be performed.
Set the type of the first task to `payment-service`.

Add the following lines to redeploy the modified process, then activate and
complete a job of the first task type:

```go
package main

import (
    "fmt"
    "github.com/zeebe-io/zeebe/clients/go/entities"
    "github.com/zeebe-io/zeebe/clients/go/worker"
    "github.com/zeebe-io/zeebe/clients/go/zbc"
    "log"
)

const brokerAddr = "0.0.0.0:26500"

func main() {
    client, err := zbc.NewZBClient(brokerAddr)
    if err != nil {
        panic(err)
    }

    // deploy workflow
    response, err := client.NewDeployWorkflowCommand().AddResourceFile("order-process.bpmn").Send()
    if err != nil {
        panic(err)
    }

    fmt.Println(response.String())

    // create a new workflow instance
    payload := make(map[string]interface{})
    payload["orderId"] = "31243"

    request, err := client.NewCreateInstanceCommand().BPMNProcessId("order-process").LatestVersion().VariablesFromMap(payload)
    if err != nil {
        panic(err)
    }

    result, err := request.Send()
    if err != nil {
        panic(err)
    }

    fmt.Println(result.String())

    jobWorker := client.NewJobWorker().JobType("payment-service").Handler(handleJob).Open()
    defer jobWorker.Close()

    jobWorker.AwaitClose()
}

func handleJob(client worker.JobClient, job entities.Job) {
    jobKey := job.GetKey()

    headers, err := job.GetCustomHeadersAsMap()
    if err != nil {
        // failed to handle job as we require the custom job headers
        failJob(client, job)
        return
    }

    variables, err := job.GetVariablesAsMap()
    if err != nil {
        // failed to handle job as we require the variables
        failJob(client, job)
        return
    }

    variables["totalPrice"] = 46.50;
    request, err := client.NewCompleteJobCommand().JobKey(jobKey).VariablesFromMap(variables)
    if err != nil {
        // failed to set the updated variables
        failJob(client, job)
        return
    }

    log.Println("Complete job", jobKey, "of type", job.Type)
    log.Println("Processing order:", variables["orderId"])
    log.Println("Collect money using payment method:", headers["method"])

    request.Send()
}

func failJob(client worker.JobClient, job entities.Job) {
    log.Println("Failed to complete job", job.GetKey())
    client.NewFailJobCommand().JobKey(job.GetKey()).Retries(job.Retries - 1).Send()
}
```

In this example we open a [job worker](/basics/job-workers.html) for jobs of type `payment-service`.
The job worker will repeatedly poll for new jobs of the type `payment-service` and activate them
subsequently. Each activated job will then be passed to the job handler which implements the business
logic of the job worker. The handler will then complete the job with its result or fail the job if
it encounters a problem while processing the job.

When you have a look at the Zeebe Monitor, then you can see that the workflow instance moved from the first service task to the next one:

![zeebe-monitor-step-2](/java-client/java-get-started-monitor-2.gif)

When you run the above example you should see similar output:

```
key:26 workflows:<bpmnProcessId:"order-process" version:2 workflowKey:2 resourceName:"order-process.bpmn" >
workflowKey:2 bpmnProcessId:"order-process" version:2 workflowInstanceKey:31
2018/11/02 11:39:50 Complete job 2 of type payment-service
2018/11/02 11:39:50 Processing order: 31243
2018/11/02 11:39:50 Collect money using payment method: VISA
```


## What's next?

Yay! You finished this tutorial and learned the basic usage of the Go client.

Next steps:
* Learn more about the [concepts behind Zeebe](/basics/README.html)
* Learn more about [BPMN workflows](/bpmn-workflows/README.html)
