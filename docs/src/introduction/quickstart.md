# Quickstart

This tutorial should help you to get to know the main concepts of Zeebe without
the need to write a single line of code.

1. [Download the Zeebe distribution](#step-1-download-the-zeebe-distribution)
1. [Start the Zeebe broker](#step-2-start-the-zeebe-broker)
1. [Create a topic](#step-3-create-a-topic)
1. [Create a job](#step-4-create-a-job)
1. [Complete a job](#step-5-complete-a-job)
1. [Create a topic subscription](#step-6-create-a-topic-subscription)
1. [Deploy a workflow](#step-7-deploy-a-workflow)
1. [Create a workflow instance](#step-8-create-a-workflow-instance)
1. [Complete the workflow instance](#step-9-complete-the-workflow-instance)
1. [Next steps](#next-steps)

> **Note:** Some command examples might not work on Windows if you use cmd or
> Powershell. For Windows users we recommend to use a bash-like shell, i.e. Git
> Bash, Cygwin or MinGW for this guide.

## Step 1: Download the Zeebe distribution

You can download the latest distribution from the [Zeebe release page](https://github.com/zeebe-io/zeebe/releases).

Extract the archive and enter the Zeebe directory.

```
tar -xzvf zeebe-distribution-X.Y.Z.tar.gz
cd zeebe-broker-X.Y.Z/
```

Inside the Zeebe directory you will find multiple directories.

```
tree -d
```
```
.
├── bin     - Binaries and start scripts of the distribution
├── conf    - Zeebe and logging configuration
└── lib     - Shared java libraries
```

## Step 2: Start the Zeebe broker

Change into the `bin/` folder and execute the `broker` file if you are using
Linux or MacOS, or the `broker.bat` file if you are using Windows. This will
start a new Zeebe broker.

```
cd bin/
./broker
```
```
09:04:17.700 [] [main] INFO  io.zeebe.broker.system - Using configuration file quickstart/zeebe-broker-X.Y.Z/conf/zeebe.cfg.toml
09:04:17.776 [] [main] INFO  io.zeebe.broker.system - Scheduler configuration: Threads{cpu-bound: 2, io-bound: 2}.
09:04:17.815 [] [main] INFO  io.zeebe.broker.system - Version: X.Y.Z
09:04:17.861 [] [main] INFO  io.zeebe.broker.clustering - Starting standalone broker.
09:04:17.865 [service-controller] [0.0.0.0:51015-zb-actors-1] INFO  io.zeebe.broker.transport - Bound managementApi.server to /0.0.0.0:51016
09:04:17.887 [service-controller] [0.0.0.0:51015-zb-actors-0] INFO  io.zeebe.transport - Bound clientApi.server to /0.0.0.0:51015
09:04:17.888 [service-controller] [0.0.0.0:51015-zb-actors-0] INFO  io.zeebe.transport - Bound replicationApi.server to /0.0.0.0:51017
09:04:17.911 [io.zeebe.broker.clustering.base.bootstrap.BootstrapSystemTopic] [0.0.0.0:51015-zb-actors-1] INFO  io.zeebe.broker.clustering - Boostrapping internal system topic 'internal-system' with replication factor 1.
09:04:18.065 [service-controller] [0.0.0.0:51015-zb-actors-0] INFO  io.zeebe.raft - Created raft internal-system-0 with configuration RaftConfiguration{heartbeatInterval='250ms', electionInterval='1s', leaveTimeout='1s'}
09:04:18.069 [io.zeebe.broker.clustering.base.bootstrap.BootstrapSystemTopic] [0.0.0.0:51015-zb-actors-0] INFO  io.zeebe.broker.clustering - Bootstrapping default topics [TopicCfg{name='default-topic', partitions=1, replicationFactor=1}]
09:04:18.122 [internal-system-0] [0.0.0.0:51015-zb-actors-1] INFO  io.zeebe.raft - Joined raft in term 0
```

You will see some output which contains the version of the broker, or different
configuration parameters like directory locations and API socket addresses.

To continue this guide open another terminal to execute commands using the
Zeebe CLI `zbctl`.

## Step 3: Create a topic

To store data in Zeebe you need a [topic](basics/topics-and-logs.html). To
create a topic you can use the `zbctl` command line tool. A binary of zbctl for
all major operation systems can be found in the `bin/` folder of the Zeebe
distribution. In the following examples we will use `zbctl`, replace this based
on the operation system you are using.

```
tree bin/
```
```
bin/
├── broker        - Zeebe broker startup script for Linux & MacOS
├── broker.bat    - Zeebe broker startup script for Windows
├── zbctl         - Zeebe CLI for Linux
├── zbctl.darwin  - Zeebe CLI for MacOS
└── zbctl.exe     - Zeebe CLI for Windows
```

To create a topic we have to specify a name, for this guide we will use the
topic name `quickstart`.

```
./bin/zbctl create topic quickstart
```
```
{
  "Name": "quickstart",
  "Partitions": 1,
  "ReplicationFactor": 1
}
```

We can now see our new topic in the topology of the Zeebe broker.

```
./bin/zbctl describe topology
```
```
+-----------------+--------------+----------------+---------+
|   TOPIC NAME    | PARTITION ID | BROKER ADDRESS |  STATE  |
+-----------------+--------------+----------------+---------+
| internal-system |            0 | 0.0.0.0:51015  | LEADER  |
+-----------------+--------------+----------------+---------+
| default-topic   |            1 | 0.0.0.0:51015  | LEADER  |
+-----------------+--------------+----------------+---------+
| quickstart      |            2 | 0.0.0.0:51015  | LEADER  |
+-----------------+--------------+----------------+---------+
```

## Step 4: Create a job

A work item in Zeebe is called a [job](basics/job-workers.html#what-is-a-job). To identify a category of work items
the job has a type specified by the user. For this example we will use the
job type `step4`. A job can have a payload which can then be used to
execute the action required for this work item. In this example we will set the
payload to contain the key `zeebe` and the value `2018`. When we create the
job we have to specify on which topic the job should be created.

> **Note:** Windows users who want to execute this command using cmd or Powershell
> have to escape the payload differently.
> - cmd: `"{\"zeebe\": 2018}"`
> - Powershell: `'{"\"zeebe"\": 2018}'`

```
./bin/zbctl --topic quickstart create job step4 --payload '{"zeebe": 2018}'
```
```
{
  "deadline": 0,
  "worker": "",
  "headers": {
    "activityId": "",
    "activityInstanceKey": -1,
    "bpmnProcessId": "",
    "workflowDefinitionVersion": -1,
    "workflowInstanceKey": -1,
    "workflowKey": -1
  },
  "customHeaders": {},
  "retries": 3,
  "type": "step4",
  "payload": "gaV6ZWViZctAn4gAAAAAAA=="
}
```

## Step 5: Complete a job

A [job worker](basics/job-workers.html#job-workers) is able to subscribe to
a specific job type to work on jobs created for this type. To create a job
worker we need to specify the topic, job type and a job handler. The job
handler is processing the work item and completes the job after it is
finished. `zbctl` allows us to specify a simple script or another external
application to handle a job. The handler will receive the payload of the job
on standard input. And has to return the updated payload on standard output.
The simplest job handler is `cat`, which is a unix command that just outputs
its input without modifying it.

> **Note:** For Windows users this command does not work with cmd as the `cat`
> command does not exist. We recommend to use Powershell or a bash-like shell
> to execute this command.

```
./bin/zbctl --topic quickstart subscribe job --jobType step4 cat
```
```
{
  "deadline": 1528355391412,
  "worker": "zbctl-rzXWdBARGH",
  "headers": {
    "activityId": "",
    "activityInstanceKey": -1,
    "bpmnProcessId": "",
    "workflowDefinitionVersion": -1,
    "workflowInstanceKey": -1,
    "workflowKey": -1
  },
  "customHeaders": {},
  "retries": 3,
  "type": "step4",
  "payload": "gaV6ZWViZctAn4gAAAAAAA=="
}
Completing Job
```

This command creates a job worker on the topic `quickstart` for the job
type `step4`. So whenever a new job of this type is created the broker will
push the job to this worker. You can try it out by opening another terminal
and repeat the command from [step 4](#step-4-create-a-job) multiple times.

> **Note:** Windows users who want to execute this command using cmd or Powershell
> have to escape the payload differently.
> - cmd: `"{\"zeebe\": 2018}"`
> - Powershell: `'{"\"zeebe"\": 2018}'`

```
./bin/zbctl --topic quickstart create job step4 --payload '{"zeebe": 2018}'
```

In the terminal with the running worker you will see that it processes every
new job.

To stop the worker press CTRL-C.

## Step 6: Create a topic subscription

You can see all events which are published to a topic by creating a topic
subscription. You have to specify the topic name.

```
./bin/zbctl --topic quickstart subscribe topic
```
```
[...]
{"Intent":"ACTIVATED","Key":4294969208,"PartitionID":2,"Position":4294970032,"Type":"JOB"}
{
  "deadline": 1528355441349,
  "worker": "zbctl-rzXWdBARGH",
  "headers": {
    "activityId": "",
    "activityInstanceKey": -1,
    "bpmnProcessId": "",
    "workflowDefinitionVersion": -1,
    "workflowInstanceKey": -1,
    "workflowKey": -1
  },
  "customHeaders": {},
  "retries": 3,
  "type": "step4",
  "payload": "gaV6ZWViZctAn4gAAAAAAA=="
}

{"Intent":"COMPLETE","Key":4294969208,"PartitionID":2,"Position":4294970360,"Type":"JOB"}
{
  "deadline": 1528355441349,
  "worker": "zbctl-rzXWdBARGH",
  "headers": {
    "activityId": "",
    "activityInstanceKey": -1,
    "bpmnProcessId": "",
    "workflowDefinitionVersion": -1,
    "workflowInstanceKey": -1,
    "workflowKey": -1
  },
  "customHeaders": {},
  "retries": 3,
  "type": "step4",
  "payload": "gaV6ZWViZctAn4gAAAAAAA=="
}

{"Intent":"COMPLETED","Key":4294969208,"PartitionID":2,"Position":4294970688,"Type":"JOB"}
{
  "deadline": 1528355441349,
  "worker": "zbctl-rzXWdBARGH",
  "headers": {
    "activityId": "",
    "activityInstanceKey": -1,
    "bpmnProcessId": "",
    "workflowDefinitionVersion": -1,
    "workflowInstanceKey": -1,
    "workflowKey": -1
  },
  "customHeaders": {},
  "retries": 3,
  "type": "step4",
  "payload": "gaV6ZWViZctAn4gAAAAAAA=="
}
```

The event stream will now contain events which describe the lifecycle of our
example jobs from type `step4`, which starts with the `CREATE` state and ends
in the `COMPLETED` state.

To stop the topic subscription press CTRL-C.

## Step 7: Deploy a workflow

A [workflow](basics/workflows.html) is used to orchestrate loosely coupled job
workers and the flow of data between them.

In this guide we will use an example process `order-process.bpmn`. You can
download it with the following link:
[order-process.bpmn](introduction/order-process.bpmn).

![order-process](introduction/order-process.png)

The process describes a sequential flow of three tasks *Collect Money*, *Fetch
Items* and *Ship Parcel*. If you open the `order-process.bpmn` file in a text
editor you will see that every task has type defined in the XML which is later
used as job type.

```
<!-- [...] -->
<bpmn:serviceTask id="collect-money" name="Collect Money">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="payment-service" />
  </bpmn:extensionElements>
</bpmn:serviceTask>
<!-- [...] -->
<bpmn:serviceTask id="fetch-items" name="Fetch Items">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="inventory-service" />
  </bpmn:extensionElements>
</bpmn:serviceTask>
<!-- [...] -->
<bpmn:serviceTask id="ship-parcel" name="Ship Parcel">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="shipment-service" />
  </bpmn:extensionElements>
</bpmn:serviceTask>
<!-- [...] -->
```

To complete an instance of this workflow we would need three job workers for
the types `payment-service`, `inventory-service` and `shipment-service`.

But first let's deploy the workflow to the Zeebe broker. We have to specify
the topic to deploy to and the resource we want to deploy, in our case the
`order-process.bpmn`.

```
./bin/zbctl --topic quickstart create workflow order-process.bpmn
```
```
{
  "TopicName": "quickstart",
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
      "ResourceName": ""
    }
  ]
}
```

## Step 8: Create a workflow instance

After the workflow is deployed we can create new instances of it. Every
instance of a workflow is a single execution of the workflow. To create a new
instance we have to specify the topic and the process ID from the BPMN file, in
our case the ID is `order-process`.

```
<bpmn:process id="order-process" isExecutable="true">
```

Every instance of a workflow normally processes some kind of data. We can
specify the initial data of the instance as payload when we start the instance.

> **Note:** Windows users who want to execute this command using cmd or Powershell
> have to escape the payload differently.
> - cmd: `"{\"orderId\": 1234}"`
> - Powershell: `'{"\"orderId"\": 1234}'`

```
./bin/zbctl --topic quickstart create instance order-process --payload '{"orderId": 1234}'
```
```
{
  "BPMNProcessID": "order-process",
  "Payload": "gadvcmRlcklky0CTSAAAAAAA",
  "Version": 1,
  "WorkflowInstanceKey": 4294971952
}
```

## Step 9: Complete the workflow instance

To complete the instance all three jobs have to be completed. Therefore we
need three job workers. Let's again use our simple `cat` worker for all three
job types. Start a job worker for all three job types as a background process
(`&`).

> **Note:** For Windows users these commands do not work with cmd as the `cat`
> command does not exist. We recommend to use Powershell or a bash-like shell
> to execute these commands.

```
./bin/zbctl --topic quickstart subscribe job --jobType payment-service cat &
./bin/zbctl --topic quickstart subscribe job --jobType inventory-service cat &
./bin/zbctl --topic quickstart subscribe job --jobType shipment-service cat &
```

To verify that our workflow instance was completed after all jobs were
processed we can again open a topic subscription. The last event should
indicate that the workflow instance was completed.

```
./bin/zbctl --topic quickstart subscribe topic
```
```
{"Intent":"END_EVENT_OCCURRED","Key":4294983224,"PartitionID":2,"Position":4294983224,"Type":"WORKFLOW_INSTANCE"}
{
  "ActivityID": "",
  "BPMNProcessID": "order-process",
  "Payload": "gadvcmRlcklky0CTSAAAAAAA",
  "Version": 1,
  "WorkflowInstanceKey": 4294971952,
  "WorkflowKey": 1
}

{"Intent":"COMPLETED","Key":4294971952,"PartitionID":2,"Position":4294983464,"Type":"WORKFLOW_INSTANCE"}
{
  "ActivityID": "",
  "BPMNProcessID": "order-process",
  "Payload": "gadvcmRlcklky0CTSAAAAAAA",
  "Version": 1,
  "WorkflowInstanceKey": 4294971952,
  "WorkflowKey": 1
}
```

As you can see in the event log the last event was a workflow instance event
with the intent `COMPLETED`, which indicates that the instance
we started in [step 8](#step-8-create-a-workflow-instance) was now completed.
We can now start new instances in another terminal with the command from [step
8](#step-8-create-a-workflow-instance).

> **Note:** Windows users who want to execute this command using cmd or Powershell
> have to escape the payload differently.
> - cmd: `"{\"orderId\": 1234}"`
> - Powershell: `'{"\"orderId"\": 1234}'`

```
./bin/zbctl --topic quickstart create instance order-process --payload '{"orderId": 1234}'
```

To close all subscriptions you can press CTRL-C to stop the topic subscription
and use the `kill` command to stop the background processes of the job workers.

```
kill %1 %2 %3
```

## Next steps

To continue working with Zeebe we recommend to get more familiar with the basic
concepts of Zeebe, see the [Basics chapter](basics/README.html) of the
documentation.

In the [BPMN Workflows chapter](bpmn-workflows/README.html) you can find an
introduction to creating Workflows with BPMN. And the [BPMN Modeler
chapter](bpmn-modeler/README.html) shows you how to model them by yourself.

The documentation also provides getting started guides for implementing job
workers using [Java](java-client/README.html) or [Go](go-client/README.html).
