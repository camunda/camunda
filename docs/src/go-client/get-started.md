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
zbctl create topic --name default-topic --partitions 1
```

You should see the output:

```
CREATED
```

Note: On Windows systems the executable is called `zbctl.exe`.

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
	"github.com/zeebe-io/zbc-go/zbc"
	"fmt"
	"errors"
	"encoding/json"
)

const BrokerAddr = "0.0.0.0:51015"

var errClientStartFailed = errors.New("cannot start client")

func main() {
	zbClient, err := zbc.NewClient(BrokerAddr)
	if err != nil {
		panic(errClientStartFailed)
	}

	b, err := json.MarshalIndent(zbClient.Cluster.TopicLeaders, "", "    ")
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
    "default-topic": [
        {
            "Host": "0.0.0.0",
            "Port": 51015,
            "TopicName": "default-topic",
            "PartitionID": 1
        }
    ],
    "internal-system": [
        {
            "Host": "0.0.0.0",
            "Port": 51015,
            "TopicName": "internal-system",
            "PartitionID": 0
        }
    ]
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

	response, err := zbClient.CreateWorkflowFromFile(topicName, zbc.BpmnXml, "order-process.bpmn")
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
  "State": "DEPLOYMENT_CREATED",
  "ResourceType": "BPMN_XML",
  "TopicName": "default-topic",
  "Resource": "..."
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
  "Version": 1,
  "Payload": "gadvcmRlcklkpTMxMjQz",
  "PayloadJSON": null
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
    response, err := zbClient.CreateWorkflowFromFile(topicName, zbc.BpmnXml, "order-process.bpmn")
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

    // open a task subscription for the payment-service task
    subscriptionCh, subscription, err := zbClient.TaskConsumer(topicName, "sample-app", "payment-service")

    osCh := make(chan os.Signal, 1)
    signal.Notify(osCh, os.Interrupt)
    go func() {
        <-osCh
        fmt.Println("Closing subscription.")
        _, err := zbClient.CloseTaskSubscription(subscription)
        if err != nil {
            fmt.Println("failed to close subscription: ", err)
        } else {
            fmt.Println("Subscription closed.")
        }
        os.Exit(0)
    }()

    for {
        message := <-subscriptionCh
        fmt.Println(message.String())

        // complete task after processing
        response, _ := zbClient.CompleteTask(message)
        fmt.Println(response)
    }
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
    "LockTime": 1510048532345,
    "LockOwner": "sample-app",
    "Headers": {
      "activityId": "collect-money",
      "activityInstanceKey": 4294979520,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 1,
      "workflowInstanceKey": 4294978536,
      "workflowKey": 4294990160
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
    "Position": 4294980824,
    "Key": 4294980040,
    "SubscriberKey": 0,
    "SubscriptionType": 0,
    "EventType": 0,
    "Event": "iKVzdGF0ZaZMT0NLRUSobG9ja1RpbWXPAAABX5XoB3mpbG9ja093bmVyqnNhbXBsZS1hcHCncmV0cmllcwOkdHlwZa9wYXltZW50LXNlcnZpY2WnaGVhZGVyc4atYnBtblByb2Nlc3NJZK1vcmRlci1wcm9jZXNzuXdvcmtmbG93RGVmaW5pdGlvblZlcnNpb24Bq3dvcmtmbG93S2V5zwAAAAEAAFlQs3dvcmtmbG93SW5zdGFuY2VLZXnPAAAAAQAAK+iqYWN0aXZpdHlJZK1jb2xsZWN0LW1vbmV5s2FjdGl2aXR5SW5zdGFuY2VLZXnPAAAAAQAAL8CtY3VzdG9tSGVhZGVyc4GmbWV0aG9kpFZJU0GncGF5bG9hZMQPgadvcmRlcklkpTMxMjQz"
  }
}
{
  "State": "COMPLETED",
  "LockTime": 1510048532345,
  "LockOwner": "sample-app",
  "Headers": {
    "activityId": "collect-money",
    "activityInstanceKey": 4294979520,
    "bpmnProcessId": "order-process",
    "workflowDefinitionVersion": 1,
    "workflowInstanceKey": 4294978536,
    "workflowKey": 4294990160
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
	"os"
	"os/signal"
)

const topicName = "default-topic"
const brokerAddr = "0.0.0.0:51015"

var errClientStartFailed = errors.New("cannot start client")

func main() {
	zbClient, err := zbc.NewClient(brokerAddr)
	if err != nil {
		panic(errClientStartFailed)
	}

	subscriptionCh, sub, err := zbClient.TopicConsumer(topicName, "subscription-name", 0)

	osCh := make(chan os.Signal, 1)
	signal.Notify(osCh, os.Interrupt)
	go func() {
		<-osCh
		fmt.Println("Closing subscription.")
		_, err := zbClient.CloseTopicSubscription(sub)
		if err != nil {
			fmt.Println("failed to close subscription: ", err)
		} else {
			fmt.Println("Subscription closed.")
		}
		os.Exit(0)
	}()

	for {
		message := <-subscriptionCh
		fmt.Println(message.String())
	}

}

```

Run the program. You should see the similar output with more events which happened during the process.

```json
{
  "Task": {
    "State": "CREATE",
    "LockTime": 9223372036854775808,
    "LockOwner": "",
    "Headers": {
      "activityId": "collect-money",
      "activityInstanceKey": 4294979520,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 1,
      "workflowInstanceKey": 4294978536,
      "workflowKey": 4294990160
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
    "Position": 4294980040,
    "Key": 4294980040,
    "SubscriberKey": 4294983400,
    "SubscriptionType": 1,
    "EventType": 0,
    "Event": "iKVzdGF0ZaZDUkVBVEWobG9ja1RpbWXTgAAAAAAAAACpbG9ja093bmVyoKdyZXRyaWVzA6R0eXBlr3BheW1lbnQtc2VydmljZadoZWFkZXJzhq1icG1uUHJvY2Vzc0lkrW9yZGVyLXByb2Nlc3O5d29ya2Zsb3dEZWZpbml0aW9uVmVyc2lvbgGrd29ya2Zsb3dLZXnPAAAAAQAAWVCzd29ya2Zsb3dJbnN0YW5jZUtlec8AAAABAAAr6KphY3Rpdml0eUlkrWNvbGxlY3QtbW9uZXmzYWN0aXZpdHlJbnN0YW5jZUtlec8AAAABAAAvwK1jdXN0b21IZWFkZXJzgaZtZXRob2SkVklTQadwYXlsb2FkxA+Bp29yZGVySWSlMzEyNDM="
  }
}
{
  "Task": {
    "State": "CREATED",
    "LockTime": 9223372036854775808,
    "LockOwner": "",
    "Headers": {
      "activityId": "collect-money",
      "activityInstanceKey": 4294979520,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 1,
      "workflowInstanceKey": 4294978536,
      "workflowKey": 4294990160
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
    "Position": 4294980432,
    "Key": 4294980040,
    "SubscriberKey": 4294983400,
    "SubscriptionType": 1,
    "EventType": 0,
    "Event": "iKVzdGF0ZadDUkVBVEVEqGxvY2tUaW1l04AAAAAAAAAAqWxvY2tPd25lcqCncmV0cmllcwOkdHlwZa9wYXltZW50LXNlcnZpY2WnaGVhZGVyc4atYnBtblByb2Nlc3NJZK1vcmRlci1wcm9jZXNzuXdvcmtmbG93RGVmaW5pdGlvblZlcnNpb24Bq3dvcmtmbG93S2V5zwAAAAEAAFlQs3dvcmtmbG93SW5zdGFuY2VLZXnPAAAAAQAAK+iqYWN0aXZpdHlJZK1jb2xsZWN0LW1vbmV5s2FjdGl2aXR5SW5zdGFuY2VLZXnPAAAAAQAAL8CtY3VzdG9tSGVhZGVyc4GmbWV0aG9kpFZJU0GncGF5bG9hZMQPgadvcmRlcklkpTMxMjQz"
  }
}
{
  "Task": {
    "State": "LOCK",
    "LockTime": 1510048532345,
    "LockOwner": "sample-app",
    "Headers": {
      "activityId": "collect-money",
      "activityInstanceKey": 4294979520,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 1,
      "workflowInstanceKey": 4294978536,
      "workflowKey": 4294990160
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
    "Position": 4294980824,
    "Key": 4294980040,
    "SubscriberKey": 4294983400,
    "SubscriptionType": 1,
    "EventType": 0,
    "Event": "iKVzdGF0ZaRMT0NLqGxvY2tUaW1lzwAAAV+V6Ad5qWxvY2tPd25lcqpzYW1wbGUtYXBwp3JldHJpZXMDpHR5cGWvcGF5bWVudC1zZXJ2aWNlp2hlYWRlcnOGrWJwbW5Qcm9jZXNzSWStb3JkZXItcHJvY2Vzc7l3b3JrZmxvd0RlZmluaXRpb25WZXJzaW9uAat3b3JrZmxvd0tlec8AAAABAABZULN3b3JrZmxvd0luc3RhbmNlS2V5zwAAAAEAACvoqmFjdGl2aXR5SWStY29sbGVjdC1tb25lebNhY3Rpdml0eUluc3RhbmNlS2V5zwAAAAEAAC/ArWN1c3RvbUhlYWRlcnOBpm1ldGhvZKRWSVNBp3BheWxvYWTED4Gnb3JkZXJJZKUzMTI0Mw=="
  }
}
{
  "Task": {
    "State": "LOCKED",
    "LockTime": 1510048532345,
    "LockOwner": "sample-app",
    "Headers": {
      "activityId": "collect-money",
      "activityInstanceKey": 4294979520,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 1,
      "workflowInstanceKey": 4294978536,
      "workflowKey": 4294990160
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
    "Position": 4294981224,
    "Key": 4294980040,
    "SubscriberKey": 4294983400,
    "SubscriptionType": 1,
    "EventType": 0,
    "Event": "iKVzdGF0ZaZMT0NLRUSobG9ja1RpbWXPAAABX5XoB3mpbG9ja093bmVyqnNhbXBsZS1hcHCncmV0cmllcwOkdHlwZa9wYXltZW50LXNlcnZpY2WnaGVhZGVyc4atYnBtblByb2Nlc3NJZK1vcmRlci1wcm9jZXNzuXdvcmtmbG93RGVmaW5pdGlvblZlcnNpb24Bq3dvcmtmbG93S2V5zwAAAAEAAFlQs3dvcmtmbG93SW5zdGFuY2VLZXnPAAAAAQAAK+iqYWN0aXZpdHlJZK1jb2xsZWN0LW1vbmV5s2FjdGl2aXR5SW5zdGFuY2VLZXnPAAAAAQAAL8CtY3VzdG9tSGVhZGVyc4GmbWV0aG9kpFZJU0GncGF5bG9hZMQPgadvcmRlcklkpTMxMjQz"
  }
}
{
  "Task": {
    "State": "COMPLETE",
    "LockTime": 1510048532345,
    "LockOwner": "sample-app",
    "Headers": {
      "activityId": "collect-money",
      "activityInstanceKey": 4294979520,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 1,
      "workflowInstanceKey": 4294978536,
      "workflowKey": 4294990160
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
    "Position": 4294981624,
    "Key": 4294980040,
    "SubscriberKey": 4294983400,
    "SubscriptionType": 1,
    "EventType": 0,
    "Event": "iKVzdGF0ZahDT01QTEVURahsb2NrVGltZc8AAAFflegHealsb2NrT3duZXKqc2FtcGxlLWFwcKdoZWFkZXJzhq1icG1uUHJvY2Vzc0lkrW9yZGVyLXByb2Nlc3O5d29ya2Zsb3dEZWZpbml0aW9uVmVyc2lvbgGrd29ya2Zsb3dLZXnPAAAAAQAAWVCzd29ya2Zsb3dJbnN0YW5jZUtlec8AAAABAAAr6KphY3Rpdml0eUlkrWNvbGxlY3QtbW9uZXmzYWN0aXZpdHlJbnN0YW5jZUtlec8AAAABAAAvwK1jdXN0b21IZWFkZXJzgaZtZXRob2SkVklTQadyZXRyaWVzA6R0eXBlr3BheW1lbnQtc2VydmljZadwYXlsb2FkxA+Bp29yZGVySWSlMzEyNDM="
  }
}
{
  "Task": {
    "State": "COMPLETED",
    "LockTime": 1510048532345,
    "LockOwner": "sample-app",
    "Headers": {
      "activityId": "collect-money",
      "activityInstanceKey": 4294979520,
      "bpmnProcessId": "order-process",
      "workflowDefinitionVersion": 1,
      "workflowInstanceKey": 4294978536,
      "workflowKey": 4294990160
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
    "Position": 4294982024,
    "Key": 4294980040,
    "SubscriberKey": 4294983400,
    "SubscriptionType": 1,
    "EventType": 0,
    "Event": "iKVzdGF0ZalDT01QTEVURUSobG9ja1RpbWXPAAABX5XoB3mpbG9ja093bmVyqnNhbXBsZS1hcHCncmV0cmllcwOkdHlwZa9wYXltZW50LXNlcnZpY2WnaGVhZGVyc4atYnBtblByb2Nlc3NJZK1vcmRlci1wcm9jZXNzuXdvcmtmbG93RGVmaW5pdGlvblZlcnNpb24Bq3dvcmtmbG93S2V5zwAAAAEAAFlQs3dvcmtmbG93SW5zdGFuY2VLZXnPAAAAAQAAK+iqYWN0aXZpdHlJZK1jb2xsZWN0LW1vbmV5s2FjdGl2aXR5SW5zdGFuY2VLZXnPAAAAAQAAL8CtY3VzdG9tSGVhZGVyc4GmbWV0aG9kpFZJU0GncGF5bG9hZMQPgadvcmRlcklkpTMxMjQz"
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
