# Quickstart

This tutorial should help you to get to know the main concepts of Zeebe without
the need to write a single line of code.

1. [Download the Zeebe distribution](#step-1-download-the-zeebe-distribution)
1. [Start the Zeebe broker](#step-2-start-the-zeebe-broker)
1. [Deploy a workflow](#step-3-deploy-a-workflow)
1. [Create a workflow instance](#step-4-create-a-workflow-instance)
1. [Complete a workflow instance](#step-5-complete-a-workflow-instance)
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

To start a Zeebe broker use the `broker` or `broker.bat` file located in the
`bin/` folder.

```
./bin/broker
```
```
13:14:41.297 [] [main] INFO  io.zeebe.util.config - Reading configuration for class class io.zeebe.broker.system.configuration.BrokerCfg from file /home/philipp/zeebe/zeebe-broker-0.17.0/conf/zeebe.cfg.toml
13:14:41.415 [] [main] INFO  io.zeebe.broker.system - Scheduler configuration: Threads{cpu-bound: 2, io-bound: 2}.
13:14:41.445 [] [main] INFO  io.zeebe.broker.system - Version: 0.17.0
13:14:41.450 [] [main] INFO  io.zeebe.broker.system - Starting broker with configuration {
  "network": {
    "host": "0.0.0.0",
    "defaultSendBufferSize": "16M",
    "portOffset": 0,
    "client": {
      "controlMessageBufferSize": "8M",
      "host": "0.0.0.0",
      "port": 26501,
      "sendBufferSize": "16M"
    },
    "management": {
      "receiveBufferSize": "8M",
      "host": "0.0.0.0",
      "port": 26502,
      "sendBufferSize": "16M"
    },
    "replication": {
      "host": "0.0.0.0",
      "port": 26503,
      "sendBufferSize": "16M"
    },
    "subscription": {
      "receiveBufferSize": "8M",
      "host": "0.0.0.0",
      "port": 26504,
      "sendBufferSize": "16M"
    }
  },
  "cluster": {
    "initialContactPoints": [],
    "partitionIds": [
      0
    ],
    "nodeId": 0,
    "partitionsCount": 1,
    "replicationFactor": 1,
    "clusterSize": 1
  },
  "threads": {
    "cpuThreadCount": 2,
    "ioThreadCount": 2
  },
  "metrics": {
    "reportingInterval": "5s",
    "file": "/home/philipp/zeebe/zeebe-broker-0.17.0/metrics/zeebe.prom",
    "enableHttpServer": false,
    "host": "0.0.0.0",
    "port": 9600
  },
  "data": {
    "directories": [
      "/home/philipp/zeebe/zeebe-broker-0.17.0/data"
    ],
    "defaultLogSegmentSize": "512M",
    "snapshotPeriod": "15m",
    "snapshotReplicationPeriod": "5m"
  },
  "gossip": {
    "retransmissionMultiplier": 3,
    "probeInterval": "1s",
    "probeTimeout": "500ms",
    "probeIndirectNodes": 3,
    "probeIndirectTimeout": "1s",
    "suspicionMultiplier": 5,
    "syncTimeout": "3s",
    "syncInterval": "15s",
    "joinTimeout": "1s",
    "joinInterval": "1s",
    "leaveTimeout": "1s",
    "maxMembershipEventsPerMessage": 32,
    "maxCustomEventsPerMessage": 8
  },
  "raft": {
    "heartbeatInterval": "250ms",
    "electionInterval": "1s",
    "leaveTimeout": "1s"
  },
  "exporters": [],
  "gateway": {
    "enable": true,
    "network": {
      "host": "0.0.0.0",
      "port": 26500
    },
    "cluster": {
      "contactPoint": "0.0.0.0:26501",
      "transportBuffer": "128M",
      "requestTimeout": "15s"
    },
    "threads": {
      "managementThreads": 1
    }
  }
}
13:14:41.529 [] [main] INFO  io.zeebe.gateway - Version: 0.17.0
13:14:41.530 [] [main] INFO  io.zeebe.gateway - Starting gateway with configuration {
  "enable": true,
  "network": {
    "host": "0.0.0.0",
    "port": 26500
  },
  "cluster": {
    "contactPoint": "0.0.0.0:26501",
    "transportBuffer": "128M",
    "requestTimeout": "15s"
  },
  "threads": {
    "managementThreads": 1
  }
}
13:14:41.537 [service-controller] [0.0.0.0:26501-zb-actors-0] INFO  io.zeebe.broker.transport - Bound subscriptionApi.server to /0.0.0.0:26504
13:14:41.539 [service-controller] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.broker.transport - Bound managementApi.server to /0.0.0.0:26502
13:14:41.604 [service-controller] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26502' on transport 'managementApi.client'
13:14:41.607 [service-controller] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26504' on transport 'subscriptionApi.client'
13:14:41.607 [service-controller] [0.0.0.0:26501-zb-actors-0] INFO  io.zeebe.transport - Bound clientApi.server to /0.0.0.0:26501
13:14:41.610 [service-controller] [0.0.0.0:26501-zb-actors-0] INFO  io.zeebe.transport - Bound replicationApi.server to /0.0.0.0:26503
13:14:41.630 [] [main] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '-1' with address '0.0.0.0:26501' on transport 'broker-client'
13:14:41.630 [] [main] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '-1' with address '0.0.0.0:26501' on transport 'broker-client-internal'
13:14:41.738 [topology] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26502' on transport 'managementApi.client'
13:14:41.738 [topology] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26503' on transport 'replicationApi.client'
13:14:41.739 [topology] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26504' on transport 'subscriptionApi.client'
13:14:41.765 [io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl] [gateway-zb-actors-0] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26501' on transport 'broker-client'
13:14:41.766 [io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl] [gateway-zb-actors-0] INFO  io.zeebe.transport.endpoint - Registering endpoint for node '0' with address '0.0.0.0:26501' on transport 'broker-client-internal'
13:14:42.032 [service-controller] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.raft - Created raft partition-0 with configuration RaftConfiguration{heartbeatInterval='250ms', electionInterval='1s', leaveTimeout='1s'}
13:14:42.222 [partition-0] [0.0.0.0:26501-zb-actors-0] INFO  io.zeebe.raft - Joined raft in term 0
13:14:42.280 [zb-stream-processor] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.broker.logstreams - Recovering state of partition 0 from snapshot
13:14:42.851 [zb-stream-processor] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.broker.logstreams - Recovered state of partition 0 from snapshot at position -1
13:14:43.002 [zb-stream-processor] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.broker.incident.processor - Start scanning the log for error events.
13:14:43.003 [zb-stream-processor] [0.0.0.0:26501-zb-actors-1] INFO  io.zeebe.broker.incident.processor - Finished scanning the log for error events.

```

You will see some output which contains the version of the broker and
configuration parameters like directory locations and API socket addresses.

To continue this guide open another terminal to execute commands using the
Zeebe CLI `zbctl`.

We can now check the status of the Zeebe broker.

```
./bin/zbctl status
```
```
Cluster size: 1
Partitions count: 1
Replication factor: 1
Brokers:
  Broker 0 - 0.0.0.0:26501
    Partition 0 : Leader
```

## Step 3: Deploy a workflow

A [workflow](basics/workflows.html) is used to orchestrate loosely coupled job
workers and the flow of data between them.

In this guide we will use an example process `order-process.bpmn`. You can
download it with the following link:
[order-process.bpmn](introduction/order-process.bpmn).

![order-process](introduction/order-process.png)

The process describes a sequential flow of three tasks *Collect Money*, *Fetch
Items* and *Ship Parcel*. If you open the `order-process.bpmn` file in a text
editor you will see that every task has an attribute `type` defined in the XML
which is later used as job type.

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
  "key": 2,
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

## Step 4: Create a workflow instance

After the workflow is deployed we can create new instances of it. Every
instance of a workflow is a single execution of the workflow. To create a new
instance we have to specify the process ID from the BPMN file, in
our case the ID is `order-process` as defined in the `order-process.bpmn`:

```
<bpmn:process id="order-process" isExecutable="true">
```

Every instance of a workflow normally processes some kind of data. We can
specify the initial data of the instance as variables when we start the instance.

> **Note:** Windows users who want to execute this command using cmd or Powershell
> have to escape the variables differently.
> - cmd: `"{\"orderId\": 1234}"`
> - Powershell: `'{"\"orderId"\": 1234}'`

```
./bin/zbctl create instance order-process --variables '{"orderId": 1234}'
```
```
{
  "workflowKey": 1,
  "bpmnProcessId": "order-process",
  "version": 1,
  "workflowInstanceKey": 3
}
```

## Step 5: Complete a workflow instance

To complete the instance all three tasks have to be executed. In Zeebe a job is
created for every task which is reached during workflow instance execution. In
order to finish a job and thereby the corresponding task it has to be activated
and completed by a [job worker](/basics/job-workers.html). A job worker is a
long living process which repeatedly tries to activate jobs for a given job
type and completes them after executing its business logic. The `zbctl` also
provides a command to spawn simple job workers using an external command or
script. The job worker will receive for every job the workflow instance variables as JSON object on
`stdin` and has to return its result also as JSON object on `stdout` if it
handled the job successfully.

In this example we use the unix command `cat` which just outputs what it receives
on `stdin`. To complete a workflow instance we now have to create a job worker for
each of the three task types from the workflow definition: `payment-service`,
`inventory-service` and `shipment-service`.

> **Note:** For Windows users this command does not work with cmd as the `cat`
> command does not exist. We recommend to use Powershell or a bash-like shell
> to execute this command.

```
./bin/zbctl create worker payment-service --handler cat &
./bin/zbctl create worker inventory-service --handler cat &
./bin/zbctl create worker shipment-service --handler cat &
```
```
2019/04/02 13:23:01 Activated job 9 with variables {"orderId":1234}
2019/04/02 13:23:01 Handler completed job 9 with variables {"orderId":1234}

2019/04/02 13:23:01 Activated job 16 with variables {"orderId":1234}
2019/04/02 13:23:01 Handler completed job 16 with variables {"orderId":1234}

2019/04/02 13:23:01 Activated job 23 with variables {"orderId":1234}
2019/04/02 13:23:01 Handler completed job 23 with variables {"orderId":1234}
```

After the job workers are running in the background we can create more instances
of our workflow to observe how the workers will complete them.

```
./bin/zbctl create instance order-process --variables '{"orderId": 12345}'
```

To close all job workers use the `kill` command to stop the background processes.

```
kill %1 %2 %3
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
