# Quickstart

This tutorial should help you to get to know the main concepts of Zeebe without
the need to write a single line of code.

1. [Download the Zeebe distribution](#step-1-download-the-zeebe-distribution)
1. [Start the Zeebe broker](#step-2-start-the-zeebe-broker)
1. [Create a job](#step-3-create-a-job)
1. [Complete a job](#step-4-complete-a-job)
1. [Deploy a workflow](#step-5-deploy-a-workflow)
1. [Create a workflow instance](#step-6-create-a-workflow-instance)
1. [Complete the workflow instance](#step-7-complete-the-workflow-instance)
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
13:59:21.973 [service-controller] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.broker.transport - Bound managementApi.server to /0.0.0.0:26502
13:59:21.973 [service-controller] [0.0.0.0:26501-zb-actors-0] INFO  io.zeebe.transport - Bound replicationApi.server to /0.0.0.0:26503
13:59:21.975 [service-controller] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.broker.transport - Bound subscriptionApi.server to /0.0.0.0:26504
13:59:22.014 [] [main] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '-1' with address '0.0.0.0:26501' on transport 'broker-client'
13:59:22.015 [] [main] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '-1' with address '0.0.0.0:26501' on transport 'broker-client-internal'
13:59:22.031 [service-controller] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.transport - Bound clientApi.server to /0.0.0.0:26501
13:59:22.031 [service-controller] [0.0.0.0:26501-zb-actors-0] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26502' on transport 'managementApi.client'
13:59:22.033 [service-controller] [0.0.0.0:26501-zb-actors-0] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26504' on transport 'subscriptionApi.client'
13:59:22.144 [topology] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26502' on transport 'managementApi.client'
13:59:22.145 [topology] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26503' on transport 'replicationApi.client'
13:59:22.145 [topology] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26504' on transport 'subscriptionApi.client'
13:59:22.169 [io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl] [client-zb-actors-0] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26501' on transport 'broker-client'
13:59:22.170 [io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl] [client-zb-actors-0] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26501' on transport 'broker-client-internal'
13:59:22.444 [] [main] INFO  io.zeebe.gateway - Gateway started using grpc server: ServerImpl{logId=2, transportServer=NettyServer{logId=1, address=/0.0.0.0:26500}}
13:59:22.453 [service-controller] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.raft - Created raft partition-0 with configuration RaftConfiguration{heartbeatInterval='250ms', electionInterval='1s', leaveTimeout='1s'}
13:59:22.505 [partition-0] [0.0.0.0:26501-zb-actors-0] INFO  io.zeebe.raft - Joined raft in term 0
13:59:22.897 [exporter] [0.0.0.0:26501-zb-actors-0] INFO  io.zeebe.broker.exporter.debug - Debug exporter opened

```

You will see some output which contains the version of the broker, or different
configuration parameters like directory locations and API socket addresses.

To continue this guide open another terminal to execute commands using the
Zeebe CLI `zbctl`.

We can now check the status of the Zeebe broker.

```
./bin/zbctl status
```
```
Broker 0.0.0.0 : 26501
  Partition 0 : Leader
```

## Step 3: Create a job

A work item in Zeebe is called a [job](basics/job-workers.html#what-is-a-job). To identify a category of work items
the job has a type specified by the user. For this example we will use the
job type `step3`. A job can have a payload which can then be used to
execute the action required for this work item. In this example we will set the
payload to contain the key `zeebe` and the value `2018`. When the job was created
the response will contain the unique key of this job instance.

> **Note:** Windows users who want to execute this command using cmd or Powershell
> have to escape the payload differently.
> - cmd: `"{\"zeebe\": 2018}"`
> - Powershell: `'{"\"zeebe"\": 2018}'`

```
./bin/zbctl create job step3 --payload '{"zeebe": 2018}'
```
```
{
  "key": 2
}

```

## Step 4: Complete a job

Before a job can be completed it has to be activated by a job worker. After a job
is activated it can be completed or failed by the worker.
`zbctl` allows us to activate jobs for a given type and afterwards complete them with an updated payload.

First activate a job of the type `step3`. The returned job contains the unique `key` of the job
which is used to complete the job later.
```
./bin/zbctl activate jobs step3
```
```
2018/10/15 13:47:35 Activated 1 for type step3
2018/10/15 13:47:35 Job 1 / 1
{
  "key": 2,
  "type": "step3",
  "jobHeaders": {
    "workflowInstanceKey": -1,
    "workflowDefinitionVersion": -1,
    "workflowKey": -1,
    "activityInstanceKey": -1
  },
  "customHeaders": "{}",
  "worker": "zbctl",
  "retries": 3,
  "deadline": 1539604355443,
  "payload": "{\"zeebe\":2018}"
}
```

After working on the job we can complete it using the `key` from the activated job.
```
./bin/zbctl complete job 2
```
```
2018/10/15 13:48:26 Completed job with key 2 and payload {}
```

## Step 5: Deploy a workflow

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

To complete an instance of this workflow we would need to activate and complete one job for each of
the types `payment-service`, `inventory-service` and `shipment-service`.

But first let's deploy the workflow to the Zeebe broker.

```
./bin/zbctl deploy order-process.bpmn
```
```
{
  "key": 1,
  "workflows": [
    {
      "bpmnProcessId": "order-process",
      "version": 1,
      "workflowKey": 1,
      "resourceName": "order-process.bpmn"
    }
  ]
}
```

## Step 6: Create a workflow instance

After the workflow is deployed we can create new instances of it. Every
instance of a workflow is a single execution of the workflow. To create a new
instance we have to specify the process ID from the BPMN file, in
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
./bin/zbctl create instance order-process --payload '{"orderId": 1234}'
```
```
{
  "workflowKey": 1,
  "bpmnProcessId": "order-process",
  "version": 1,
  "workflowInstanceKey": 6
}
```

## Step 7: Complete the workflow instance

To complete the instance all three jobs have to be completed. Therefore we
need activate and complete one job per type.

First activate a job for the `payment-service` type.
```
./bin/zbctl activate jobs payment-service
```
```
2018/10/15 13:55:00 Activated 1 for type payment-service
2018/10/15 13:55:00 Job 1 / 1
{
  "key": 12,
  "type": "payment-service",
  "jobHeaders": {
    "workflowInstanceKey": 6,
    "bpmnProcessId": "order-process",
    "workflowDefinitionVersion": 1,
    "workflowKey": 1,
    "activityId": "collect-money",
    "activityInstanceKey": 21
  },
  "customHeaders": "{\"method\":\"VISA\"}",
  "worker": "zbctl",
  "retries": 3,
  "deadline": 1539604800511,
  "payload": "{\"orderId\":1234}"
}
```
And complete it by its job key `12`.
```
./bin/zbctl complete job 12
```
```
2018/10/15 13:55:08 Completed job with key 12 and payload {}
```

Next activate a job for the `inventory-service` type.

```
./bin/zbctl activate jobs inventory-service
```
```
2018/10/15 13:55:18 Activated 1 for type inventory-service
2018/10/15 13:55:18 Job 1 / 1
{
  "key": 22,
  "type": "inventory-service",
  "jobHeaders": {
    "workflowInstanceKey": 6,
    "bpmnProcessId": "order-process",
    "workflowDefinitionVersion": 1,
    "workflowKey": 1,
    "activityId": "fetch-items",
    "activityInstanceKey": 31
  },
  "customHeaders": "{}",
  "worker": "zbctl",
  "retries": 3,
  "deadline": 1539604818042,
  "payload": "{\"orderId\":1234}"
}
```

And complete it by its job key `22`.
```
./bin/zbctl complete job 22
```
```
2018/10/15 13:55:20 Completed job with key 22 and payload {}
```

Last activate a job for the `shipment-service` type.
```
./bin/zbctl activate jobs shipment-service
```
```
2018/10/15 13:55:31 Activated 1 for type shipment-service
2018/10/15 13:55:31 Job 1 / 1
{
  "key": 32,
  "type": "shipment-service",
  "jobHeaders": {
    "workflowInstanceKey": 6,
    "bpmnProcessId": "order-process",
    "workflowDefinitionVersion": 1,
    "workflowKey": 1,
    "activityId": "ship-parcel",
    "activityInstanceKey": 41
  },
  "customHeaders": "{}",
  "worker": "zbctl",
  "retries": 3,
  "deadline": 1539604831656,
  "payload": "{\"orderId\":1234}"
}
```

And complete it by its job key `32`.
```
./bin/zbctl complete job 32
```
```
2018/10/15 13:55:33 Completed job with key 32 and payload {}
```

If you want to visualize the state of the workflow instances you can start the
[Zeebe simple monitor](https://github.com/zeebe-io/zeebe-simple-monitor).

## Next steps

To continue working with Zeebe we recommend to get more familiar with the basic
concepts of Zeebe, see the [Basics chapter](basics/README.html) of the
documentation.

In the [BPMN Workflows chapter](bpmn-workflows/README.html) you can find an
introduction to creating Workflows with BPMN. And the [BPMN Modeler
chapter](bpmn-modeler/README.html) shows you how to model them by yourself.

The documentation also provides getting started guides for implementing job
workers using [Java](java-client/README.html) or [Go](go-client/README.html).
