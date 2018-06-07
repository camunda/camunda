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

* Go v1.9+ environment installed
* [Zeebe distribution](../introduction/install.html)
* [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases)
* [Zeebe Monitor](https://github.com/zeebe-io/zeebe-simple-monitor/releases)


Now, start the Zeebe broker.

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

First, we need a new Go project.
Create a new project using your IDE, or create new Go module with:

```
mkdir -p $GOPATH/src/github.com/{{your username}}/zb-example
cd $GOPATH/src/github.com/{{your username}}/zb-example
```

Install Zeebe Go client library:

```
go get github.com/zeebe-io/zbc-go/zbc
```

Create a main.go file inside the module and add the following lines to bootstrap the Zeebe client:

```go
package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"github.com/zeebe-io/zbc-go/zbc"
)

const BrokerAddr = "0.0.0.0:51015"

var errClientStartFailed = errors.New("cannot start client")

func main() {
	zbClient, err := zbc.NewClient(BrokerAddr)
	if err != nil {
		panic(errClientStartFailed)
	}

	topology, err := zbClient.GetTopology()
	if err != nil {
		panic(err)
	}

	b, err := json.MarshalIndent(topology, "", "    ")
	fmt.Println(string(b))
}
```

Run the program.

```bash
go run main.go
```
You should see similar output:

```json
{
    "AddrByPartitionID": {
        "0": "0.0.0.0:51015",
        "1": "0.0.0.0:51015"
    },
    "PartitionIDByTopicName": {
        "default-topic": [
            1
        ],
        "internal-system": [
            0
        ]
    },
    "Brokers": [
        {
            "Host": "0.0.0.0",
            "Port": 51015,
            "Partitions": [
                {
                    "State": "LEADER",
                    "TopicName": "internal-system",
                    "PartitionID": 0,
                    "ReplicationFactor": 1
                },
                {
                    "State": "LEADER",
                    "TopicName": "default-topic",
                    "PartitionID": 1,
                    "ReplicationFactor": 1
                }
            ]
        }
    ],
    "UpdatedAt": "2018-06-07T13:23:44.442722715+02:00"
}
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
	"errors"
	"fmt"
	"github.com/zeebe-io/zbc-go/zbc"
	"github.com/zeebe-io/zbc-go/zbc/common"
)

const topicName = "default-topic"
const brokerAddr = "0.0.0.0:51015"

var errClientStartFailed = errors.New("cannot start client")
var errWorkflowDeploymentFailed = errors.New("creating new workflow deployment failed")

func main() {
	zbClient, err := zbc.NewClient(brokerAddr)
	if err != nil {
		panic(errClientStartFailed)
	}

	response, err := zbClient.CreateWorkflowFromFile(topicName, zbcommon.BpmnXml, "order-process.bpmn")
	if err != nil {
		panic(errWorkflowDeploymentFailed)
	}

	fmt.Println(response.String())
}
```

Run the program and verify that the workflow is deployed successfully.
You should see similar the output:

```json
{
  "TopicName": "default-topic",
  "Resources": [
    {
      "Resource": "[...]",
      "ResourceType": "BPMN_XML",
      "ResourceName": "order-process.bpmn"
    }
  ],
  "DeployedWorkflows": [
    {
      "BpmnProcessId": "order-process",
      "Version": 1,
      "WorkflowKey": 1,
      "ResourceName": "order-process.bpmn"
    }
  ]
}

```

We can also deploy the workflow using command line utility:

```
zbctl create workflow order-process.bpmn
```


## Create a workflow instance

Finally, we are ready to create a first instance of the deployed workflow.  A
workflow instance is created of a specific version of the workflow, which can
be set on creation. If the version is set to `-1` the latest version of the
workflow is used.

```go
package main

import (
	"errors"
	"github.com/zeebe-io/zbc-go/zbc"
	"fmt"
)

const topicName = "default-topic"
const brokerAddr = "0.0.0.0:51015"

var errClientStartFailed = errors.New("cannot start client")

func main() {
	zbClient, err := zbc.NewClient(brokerAddr)
	if err != nil {
		panic(errClientStartFailed)
	}

	// After the workflow is deployed.
	payload := make(map[string]interface{})
	payload["orderId"] = "31243"

	instance := zbc.NewWorkflowInstance("order-process", -1, payload)
	msg, err := zbClient.CreateWorkflowInstance(topicName, instance)

	if err != nil {
		panic(err)
	}

	fmt.Println(msg.String())
}
```

Run the program and verify that the workflow instance is created. You should see the output:

```json
{
  "BPMNProcessID": "order-process",
  "Payload": "gadvcmRlcklkpTMxMjQz",
  "Version": 1,
  "WorkflowInstanceKey": 4294967400
}
```

You did it! You want to see how the workflow instance is executed?

Start the Zeebe Monitor using `java -jar zeebe-simple-monitor.jar`.

Open a web browser and go to <http://localhost:8080/>.

Connect to the broker and switch to the workflow instances view.
Here, you see the current state of the workflow instance which includes active jobs, completed activities, the payload and open incidents.

![zeebe-monitor-step-1](/java-client/zeebe-monitor-1.png)


## Work on a task

Now we want to do some work within your workflow.  First, add a few service
tasks to the BPMN diagram and set the required attributes.  Then extend your
`main.go` file and open a job subscription to process jobs which are created
when the workflow instance reaches a service task.

Open the BPMN diagram in the Zeebe Modeler.
Insert a few service tasks between the start and the end event.

![model-workflow-step-2](/go-client/order-process.png)

You need to set the type of each task, which identifies the nature of the work to be performed.
Set the type of the first task to `payment-service`.

Add the following lines to redeploy the modified process and open a job
subscription for the first tasks type:

```go
package main

import (
	"errors"
	"fmt"
	"github.com/zeebe-io/zbc-go/zbc"
	"github.com/zeebe-io/zbc-go/zbc/common"
	"github.com/zeebe-io/zbc-go/zbc/models/zbsubscriptions"
	"github.com/zeebe-io/zbc-go/zbc/services/zbsubscribe"
	"os"
	"os/signal"
)

const topicName = "default-topic"
const brokerAddr = "0.0.0.0:51015"

var errClientStartFailed = errors.New("cannot start client")

func main() {
	zbClient, err := zbc.NewClient(brokerAddr)
	if err != nil {
		panic(err)
	}

	// deploy workflow
	response, err := zbClient.CreateWorkflowFromFile(topicName, zbcommon.BpmnXml, "order-process.bpmn")
	if err != nil {
		panic(err)
	}

	fmt.Println(response.String())

	// create a new workflow instance
	payload := make(map[string]interface{})
	payload["orderId"] = "31243"

	instance := zbc.NewWorkflowInstance("order-process", -1, payload)
	msg, err := zbClient.CreateWorkflowInstance(topicName, instance)

	if err != nil {
		panic(err)
	}

	fmt.Println(msg.String())

	subscription, err := zbClient.JobSubscription(topicName, "sample-app", "payment-service", 1000, 32, func(client zbsubscribe.ZeebeAPI, event *zbsubscriptions.SubscriptionEvent) {
		fmt.Println(event.String())

		// complete job after processing
		response, _ := client.CompleteJob(event)
		fmt.Println(response)
	})

	if err != nil {
		panic("Unable to open subscription")
	}

	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)
	go func() {
		<-c
		err := subscription.Close()
		if err != nil {
			panic("Failed to close subscription")
		}

		fmt.Println("Closed subscription")
		os.Exit(0)
	}()

	subscription.Start()
}
```

In this example we shall open a job subscription for the previously created workflow instance, consume the job and complete it. Before completing it it shall print the data to the standard output.
When you have a look at the Zeebe Monitor, then you can see that the workflow instance moved from the first service task to the next one:

![zeebe-monitor-step-2](/go-client/zeebe-monitor-2.png)

When you run the above example you should see similar output:

```json
{
  "Event": {
    "deadline": 1528370870456,
    "worker": "sample-app",
    "headers": {
      "activityId": "collect-money",
      "activityInstanceKey": 4294969624,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 2,
      "workflowInstanceKey": 4294968744,
      "workflowKey": 2
    },
    "customHeaders": {
      "method": "VISA"
    },
    "retries": 3,
    "type": "payment-service",
    "payload": "gadvcmRlcklkpTMxMjQz"
  },
  "Metadata": {
    "PartitionId": 1,
    "Position": 4294970840,
    "Key": 4294970088,
    "SubscriberKey": 0,
    "RecordType": 0,
    "SubscriptionType": 0,
    "ValueType": 0,
    "Intent": 3,
    "Timestamp": 1528370869456,
    "Value": "[...]"
  }
}
{
  "deadline": 1528370870456,
  "worker": "sample-app",
  "headers": {
    "activityId": "collect-money",
    "activityInstanceKey": 4294969624,
    "bpmnProcessId": "order-process",
    "workflowDefinitionVersion": 2,
    "workflowInstanceKey": 4294968744,
    "workflowKey": 2
  },
  "customHeaders": {
    "method": "VISA"
  },
  "retries": 3,
  "type": "payment-service",
  "payload": "gadvcmRlcklkpTMxMjQz"
}
```

To stop the service hit CTRL+C which will trigger closing of the subscription on the broker and stopping the service.


## Open a topic subscription

The Zeebe Monitor consume the events of the broker to build the monitoring.
You can see all received events in the log view.
In order to build something similar for our application, we open a [topic subscription] and print all workflow instance events.

When the topic subscription is open, then we receive all events which are written during execution of the workflow instance.
The given handler is invoked for each received event.

Add the following lines to the main class to print all events:

```go
package main

import (
	"errors"
	"fmt"
	"github.com/zeebe-io/zbc-go/zbc"
	"github.com/zeebe-io/zbc-go/zbc/models/zbsubscriptions"
	"github.com/zeebe-io/zbc-go/zbc/services/zbsubscribe"
	"os"
	"os/signal"
)

const topicName = "default-topic"
const brokerAddr = "0.0.0.0:51015"

var errClientStartFailed = errors.New("cannot start client")

func handler(client zbsubscribe.ZeebeAPI, event *zbsubscriptions.SubscriptionEvent) error {
	fmt.Printf("Event: %v\n", event)
	return nil
}

func main() {
	zbClient, err := zbc.NewClient(brokerAddr)
	if err != nil {
		panic(errClientStartFailed)
	}

	subscription, err := zbClient.TopicSubscription(topicName, "subscrition-name", 128, 0, true, handler)

	if err != nil {
		panic("Failed to open subscription")
	}

	osCh := make(chan os.Signal, 1)
	signal.Notify(osCh, os.Interrupt)
	go func() {
		<-osCh
		err := subscription.Close()
		if err != nil {
			panic("Failed to close subscription")
		}
		fmt.Println("Subscription closed.")
		os.Exit(0)
	}()

	subscription.Start()
}
```

Run the program. You should see the similar output with more events which happened during the process.

```json
Event: {
  "Event": {
    "deadline": 9223372036854775808,
    "worker": "",
    "headers": {
      "activityId": "fetch-items",
      "activityInstanceKey": 4294973080,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 2,
      "workflowInstanceKey": 4294968744,
      "workflowKey": 2
    },
    "customHeaders": {},
    "retries": 3,
    "type": "inventory-service",
    "payload": "gadvcmRlcklkpTMxMjQz"
  },
  "Metadata": {
    "PartitionId": 1,
    "Position": 4294973544,
    "Key": 4294973544,
    "SubscriberKey": 4294974264,
    "RecordType": 1,
    "SubscriptionType": 1,
    "ValueType": 0,
    "Intent": 0,
    "Timestamp": 1528370869461,
    "Value": "[...]"
  }
}
Event: {
  "Event": {
    "deadline": 9223372036854775808,
    "worker": "",
    "headers": {
      "activityId": "fetch-items",
      "activityInstanceKey": 4294973080,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 2,
      "workflowInstanceKey": 4294968744,
      "workflowKey": 2
    },
    "customHeaders": {},
    "retries": 3,
    "type": "inventory-service",
    "payload": "gadvcmRlcklkpTMxMjQz"
  },
  "Metadata": {
    "PartitionId": 1,
    "Position": 4294973904,
    "Key": 4294973544,
    "SubscriberKey": 4294974264,
    "RecordType": 0,
    "SubscriptionType": 1,
    "ValueType": 0,
    "Intent": 1,
    "Timestamp": 1528370869462,
    "Value": "[...]"
  }
}
```

Each of these events represents one step in the workflow instance life cycle.

When we have a look at the Zeebe Monitor, then we can see how the payload is modified after the activity:

![zeebe-monitor-step-3](/go-client/zeebe-monitor-3.png)


Again to stop the process, hit CTRL + C which will gracefully shut down the subscription on the broker and stop the process.


## What's next?

Yay! You finished this tutorial and learned the basic usage of the Go client.

Next steps:
* Learn more about the [concepts behind Zeebe](/basics/README.html)
* Learn more about [BPMN workflows](/bpmn-workflows/README.html)

[topic subscription]: ../basics/topics-and-logs.html
[job subscription]: ../basics/job-workers.html
