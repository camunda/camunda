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

* Go v1.5+ environment installed
* [Zeebe distribution](../introduction/install.html)
* [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases)
* [Zeebe Monitor](https://github.com/zeebe-io/zeebe-simple-monitor/releases)


Now, start the Zeebe broker.

Create a [topic](../basics/topics-and-logs.html) named `default-topic`. If you have done this already for your Zeebe installation, you can skip this step.

Create the topic with zbctl by executing the following command on the command line:

```
zbctl create topic default-topic --partitions 1
```

You should see the output:

```
{
  "Name": "default-topic",
  "State": "CREATED",
  "Partitions": 1
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

	topology, err := zbClient.RefreshTopology()
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
        "0": "localhost:51015",
        "1": "localhost:51015"
    },
    "PartitionIDByTopicName": {
        "default-topic": [
            1
        ]
    },
    "Brokers": [
        {
            "Host": "localhost",
            "Port": 51015,
            "Partitions": [
                {
                    "State": "LEADER",
                    "TopicName": "internal-system",
                    "PartitionID": 0
                },
                {
                    "State": "LEADER",
                    "TopicName": "default-topic",
                    "PartitionID": 1
                }
            ]
        }
    ],
    "UpdatedAt": "2018-03-29T11:26:32.961365972+02:00"
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
  "State": "CREATED",
  "TopicName": "default-topic",
  "Resources": [
    {
      "Resource": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPGJwbW46ZGVmaW5pdGlvbnMgeG1sbnM6YnBtbj0iaHR0cDovL3d3dy5vbWcub3JnL3NwZWMvQlBNTi8yMDEwMDUyNC9NT0RFTCIgeG1sbnM6YnBtbmRpPSJodHRwOi8vd3d3Lm9tZy5vcmcvc3BlYy9CUE1OLzIwMTAwNTI0L0RJIiB4bWxuczpkaT0iaHR0cDovL3d3dy5vbWcub3JnL3NwZWMvREQvMjAxMDA1MjQvREkiIHhtbG5zOmRjPSJodHRwOi8vd3d3Lm9tZy5vcmcvc3BlYy9ERC8yMDEwMDUyNC9EQyIgeG1sbnM6eHNpPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYS1pbnN0YW5jZSIgaWQ9IkRlZmluaXRpb25zXzEiIHRhcmdldE5hbWVzcGFjZT0iaHR0cDovL2JwbW4uaW8vc2NoZW1hL2JwbW4iIGV4cG9ydGVyPSJaZWViZSBNb2RlbGVyIiBleHBvcnRlclZlcnNpb249IjAuMS4wIj4KICAgIDxicG1uOnByb2Nlc3MgaWQ9Im9yZGVyLXByb2Nlc3MiIGlzRXhlY3V0YWJsZT0idHJ1ZSI+CiAgICAgICAgPGJwbW46c3RhcnRFdmVudCBpZD0ib3JkZXItcGxhY2VkIiBuYW1lPSJPcmRlciBQbGFjZWQiPgogICAgICAgICAgICA8YnBtbjpvdXRnb2luZz5TZXF1ZW5jZUZsb3dfMTh0cWthNTwvYnBtbjpvdXRnb2luZz4KICAgICAgICA8L2JwbW46c3RhcnRFdmVudD4KICAgICAgICA8YnBtbjplbmRFdmVudCBpZD0ib3JkZXItZGVsaXZlcmVkIiBuYW1lPSJPcmRlciBEZWxpdmVyZWQiPgogICAgICAgICAgICA8YnBtbjppbmNvbWluZz5TZXF1ZW5jZUZsb3dfMTh0cWthNTwvYnBtbjppbmNvbWluZz4KICAgICAgICA8L2JwbW46ZW5kRXZlbnQ+CiAgICAgICAgPGJwbW46c2VxdWVuY2VGbG93IGlkPSJTZXF1ZW5jZUZsb3dfMTh0cWthNSIgc291cmNlUmVmPSJvcmRlci1wbGFjZWQiIHRhcmdldFJlZj0ib3JkZXItZGVsaXZlcmVkIiAvPgogICAgPC9icG1uOnByb2Nlc3M+CiAgICA8YnBtbmRpOkJQTU5EaWFncmFtIGlkPSJCUE1ORGlhZ3JhbV8xIj4KICAgICAgICA8YnBtbmRpOkJQTU5QbGFuZSBpZD0iQlBNTlBsYW5lXzEiIGJwbW5FbGVtZW50PSJvcmRlci1wcm9jZXNzIj4KICAgICAgICAgICAgPGJwbW5kaTpCUE1OU2hhcGUgaWQ9Il9CUE1OU2hhcGVfU3RhcnRFdmVudF8yIiBicG1uRWxlbWVudD0ib3JkZXItcGxhY2VkIj4KICAgICAgICAgICAgICAgIDxkYzpCb3VuZHMgeD0iMTczIiB5PSIxMDIiIHdpZHRoPSIzNiIgaGVpZ2h0PSIzNiIgLz4KICAgICAgICAgICAgICAgIDxicG1uZGk6QlBNTkxhYmVsPgogICAgICAgICAgICAgICAgICAgIDxkYzpCb3VuZHMgeD0iMTU5IiB5PSIxMzgiIHdpZHRoPSI2NSIgaGVpZ2h0PSIxMiIgLz4KICAgICAgICAgICAgICAgIDwvYnBtbmRpOkJQTU5MYWJlbD4KICAgICAgICAgICAgPC9icG1uZGk6QlBNTlNoYXBlPgogICAgICAgICAgICA8YnBtbmRpOkJQTU5TaGFwZSBpZD0iRW5kRXZlbnRfMTI1M3N0cV9kaSIgYnBtbkVsZW1lbnQ9Im9yZGVyLWRlbGl2ZXJlZCI+CiAgICAgICAgICAgICAgICA8ZGM6Qm91bmRzIHg9IjM2MyIgeT0iMTAyIiB3aWR0aD0iMzYiIGhlaWdodD0iMzYiIC8+CiAgICAgICAgICAgICAgICA8YnBtbmRpOkJQTU5MYWJlbD4KICAgICAgICAgICAgICAgICAgICA8ZGM6Qm91bmRzIHg9IjM0MiIgeT0iMTQxIiB3aWR0aD0iNzgiIGhlaWdodD0iMTIiIC8+CiAgICAgICAgICAgICAgICA8L2JwbW5kaTpCUE1OTGFiZWw+CiAgICAgICAgICAgIDwvYnBtbmRpOkJQTU5TaGFwZT4KICAgICAgICAgICAgPGJwbW5kaTpCUE1ORWRnZSBpZD0iU2VxdWVuY2VGbG93XzE4dHFrYTVfZGkiIGJwbW5FbGVtZW50PSJTZXF1ZW5jZUZsb3dfMTh0cWthNSI+CiAgICAgICAgICAgICAgICA8ZGk6d2F5cG9pbnQgeHNpOnR5cGU9ImRjOlBvaW50IiB4PSIyMDkiIHk9IjEyMCIgLz4KICAgICAgICAgICAgICAgIDxkaTp3YXlwb2ludCB4c2k6dHlwZT0iZGM6UG9pbnQiIHg9IjM2MyIgeT0iMTIwIiAvPgogICAgICAgICAgICAgICAgPGJwbW5kaTpCUE1OTGFiZWw+CiAgICAgICAgICAgICAgICAgICAgPGRjOkJvdW5kcyB4PSIyODYiIHk9Ijk4IiB3aWR0aD0iMCIgaGVpZ2h0PSIxMyIgLz4KICAgICAgICAgICAgICAgIDwvYnBtbmRpOkJQTU5MYWJlbD4KICAgICAgICAgICAgPC9icG1uZGk6QlBNTkVkZ2U+CiAgICAgICAgPC9icG1uZGk6QlBNTlBsYW5lPgogICAgPC9icG1uZGk6QlBNTkRpYWdyYW0+CjwvYnBtbjpkZWZpbml0aW9ucz4=",
      "ResourceType": "BPMN_XML",
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
  "State": "WORKFLOW_INSTANCE_CREATED",
  "BPMNProcessID": "order-process",
  "Version": 2,
  "Payload": "gadvcmRlcklkpTMxMjQz",
  "PayloadJSON": null,
  "WorkflowInstanceKey": 4294976896
}
```

You did it! You want to see how the workflow instance is executed?

Start the Zeebe Monitor using `java -jar zeebe-simple-monitor.jar`.

Open a web browser and go to <http://localhost:8080/>.

Connect to the broker and switch to the workflow instances view.
Here, you see the current state of the workflow instance which includes active tasks, completed activities, the payload and open incidents.

![zeebe-monitor-step-1](/java-client/zeebe-monitor-1.png)


## Work on a task

Now we want to do some work within your workflow.  First, add a few service
tasks to the BPMN diagram and set the required attributes.  Then extend your
`main.go` file and open a task subscription to process tasks which are created
when the workflow instance reaches a service task.

Open the BPMN diagram in the Zeebe Modeler.
Insert a few service tasks between the start and the end event.

![model-workflow-step-2](/go-client/order-process.png)

You need to set the type of each task, which identifies the nature of the work to be performed.
Set the type of the first task to `payment-service`.

Add the following lines to redeploy the modified process and open a task
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

	subscription, err := zbClient.TaskSubscription(topicName, "sample-app", "payment-service", 32, func(client zbsubscribe.ZeebeAPI, event *zbsubscriptions.SubscriptionEvent) {
		fmt.Println(event.String())

		// complete task after processing
		response, _ := client.CompleteTask(event)
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

In this example we shall open a task subscription for the previously created workflow instance, consume the task and complete it. Before completing it it shall print the data to the standard output.
When you have a look at the Zeebe Monitor, then you can see that the workflow instance moved from the first service task to the next one:

![zeebe-monitor-step-2](/go-client/zeebe-monitor-2.png)

When you run the above example you should see similar output:


```json
{
  "Task": {
    "State": "LOCKED",
    "LockTime": 1522316110702,
    "LockOwner": "sample-app",
    "Headers": {
      "activityId": "collect-money",
      "activityInstanceKey": 4294990088,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 3,
      "workflowInstanceKey": 4294989104,
      "workflowKey": 4295003848
    },
    "CustomHeader": {
      "method": "VISA"
    },
    "Retries": 3,
    "Type": "payment-service",
    "Payload": "gadvcmRlcklkpTMxMjQz"
  },
  "Event": {
    "PartitionId": 1,
    "Position": 4294991392,
    "Key": 4294990608,
    "SubscriberKey": 0,
    "SubscriptionType": 0,
    "EventType": 0,
    "Event": "iKVzdGF0ZaZMT0NLRUSobG9ja1RpbWXPAAABYnEca26pbG9ja093bmVyqnNhbXBsZS1hcHCncmV0cmllcwOkdHlwZa9wYXltZW50LXNlcnZpY2WnaGVhZGVyc4atYnBtblByb2Nlc3NJZK1vcmRlci1wcm9jZXNzuXdvcmtmbG93RGVmaW5pdGlvblZlcnNpb24Dq3dvcmtmbG93S2V5zwAAAAEAAI7Is3dvcmtmbG93SW5zdGFuY2VLZXnPAAAAAQAAVTCqYWN0aXZpdHlJZK1jb2xsZWN0LW1vbmV5s2FjdGl2aXR5SW5zdGFuY2VLZXnPAAAAAQAAWQitY3VzdG9tSGVhZGVyc4GmbWV0aG9kpFZJU0GncGF5bG9hZMQPgadvcmRlcklkpTMxMjQz"
  }
}
{
  "State": "COMPLETED",
  "LockTime": 1522316110702,
  "LockOwner": "sample-app",
  "Headers": {
    "activityId": "collect-money",
    "activityInstanceKey": 4294990088,
    "bpmnProcessId": "order-process",
    "workflowDefinitionVersion": 3,
    "workflowInstanceKey": 4294989104,
    "workflowKey": 4295003848
  },
  "CustomHeader": {
    "method": "VISA"
  },
  "Retries": 3,
  "Type": "payment-service",
  "Payload": "gadvcmRlcklkpTMxMjQz"
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
  "Task": null,
  "Event": {
    "PartitionId": 1,
    "Position": 4295008568,
    "Key": 4295005656,
    "SubscriberKey": 4295009536,
    "SubscriptionType": 1,
    "EventType": 5,
    "Event": "h6VzdGF0ZbNBQ1RJVklUWV9DT01QTEVUSU5HrWJwbW5Qcm9jZXNzSWStb3JkZXItcHJvY2Vzc6d2ZXJzaW9uBKt3b3JrZmxvd0tlec8AAAABAAD4wLN3b3JrZmxvd0luc3RhbmNlS2V5zwAAAAEAAJIAqmFjdGl2aXR5SWStY29sbGVjdC1tb25leadwYXlsb2FkxA+Bp29yZGVySWSlMzEyNDM="
  }
}
Event: {
  "Task": null,
  "Event": {
    "PartitionId": 1,
    "Position": 4295008832,
    "Key": 4295008832,
    "SubscriberKey": 4295009536,
    "SubscriptionType": 1,
    "EventType": 6,
    "Event": "iqVzdGF0ZaZDUkVBVEWpZXJyb3JUeXBlsElPX01BUFBJTkdfRVJST1KsZXJyb3JNZXNzYWdl2SVObyBkYXRhIGZvdW5kIGZvciBxdWVyeSAkLnRvdGFsUHJpY2UutGZhaWx1cmVFdmVudFBvc2l0aW9uzwAAAAEAAKE4rWJwbW5Qcm9jZXNzSWStb3JkZXItcHJvY2Vzc7N3b3JrZmxvd0luc3RhbmNlS2V5zwAAAAEAAJIAqmFjdGl2aXR5SWStY29sbGVjdC1tb25lebNhY3Rpdml0eUluc3RhbmNlS2V5zwAAAAEAAJXYp3Rhc2tLZXn/p3BheWxvYWTEAYA="
  }
}
Event: {
  "Task": null,
  "Event": {
    "PartitionId": 1,
    "Position": 4295009184,
    "Key": 4295008832,
    "SubscriberKey": 4295009536,
    "SubscriptionType": 1,
    "EventType": 6,
    "Event": "iqVzdGF0ZadDUkVBVEVEqWVycm9yVHlwZbBJT19NQVBQSU5HX0VSUk9SrGVycm9yTWVzc2FnZdklTm8gZGF0YSBmb3VuZCBmb3IgcXVlcnkgJC50b3RhbFByaWNlLrRmYWlsdXJlRXZlbnRQb3NpdGlvbs8AAAABAAChOK1icG1uUHJvY2Vzc0lkrW9yZGVyLXByb2Nlc3Ozd29ya2Zsb3dJbnN0YW5jZUtlec8AAAABAACSAKphY3Rpdml0eUlkrWNvbGxlY3QtbW9uZXmzYWN0aXZpdHlJbnN0YW5jZUtlec8AAAABAACV2Kd0YXNrS2V5/6dwYXlsb2FkxAGA"
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
[task subscription]: ../basics/task-workers.html
