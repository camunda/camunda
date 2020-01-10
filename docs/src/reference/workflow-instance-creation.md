# Workflow Instance Creation

Depending on the workflow definition, an instance of it can be created in the following ways.
* by a create workflow instance command
* by an occured event (eg:- timer, message)

## Create workflow instance command

A workflow instance can be created by sending a command specifying the BPMN process id or the unique key of the workflow.
There are two commands to create a workflow instance.

### Create and Execute Asynchronously

 A workflow that has a [none start event](/bpmn-workflows/none-events/none-events.html#none-start-events) can be started explicitly using the command [CreateWorkflowInstance](/reference/grpc.html#createworkflowinstance-rpc).
 When the broker receives this commands, it creates a new workflow instance and immediately respond with the workflow instance id.
 The execution of the workflow happens after the response is send.

 ![create-workflow](/reference/workflow-instance-creation/create-workflow.png)

 <details>
   <summary>Code example</summary>
   <p>Create a workflow instance:

```
zbctl create instance "order-process"
```
   Response:
```
{
 "workflowKey": 2251799813685249,
 "bpmnProcessId": "order-process",
 "version": 1,
 "workflowInstanceKey": 2251799813686019
}

```

   </p>
 </details>

### Create and Await Results

Typically, workflow creation and execution are decoupled.
However, there are use-cases that need to collect the results of a workflow when it's execution is completed.
The [CreateWorkflowInstanceWithResult](/reference/grpc.html#createworkflowinstancewithresult-rpc) command allows you to “synchronously” execute workflows and receive the results via a set of variables.
The response is send when the workflow execution is completed.

 ![create-workflow](/reference/workflow-instance-creation/create-workflow-with-result.png)

Failure scenarios that are applicable to other commands are applicable to this command. Clients may not get a response in the following cases even if the workflow execution is completed successfully.
- Leader failover: When the broker that is processing this workflow crashed, another broker continues the processing. But it does not send the response because the request is registered on the other broker.
- Gateway failure: If the gateway to which the client is connected failed, broker cannot send the response to the client.
- gRPC timeout: If the gRPC deadlines are not configured for long request timeout, the connection may be closed before the workflow is completed.

This command is typically useful for short running workflows and workflows that collect information.
If the workflow mutates system state, or further operations rely on the workflow outcome response to the client, take care to consider and design your system for failure states and retries.
Note that, when the client resend the command, it creates a new workflow instance.

<details>
  <summary>Code example</summary>
  <p>Create a workflow instance and await results:

```
zbctl create instance "order-process" --withResult --variables '{"orderId": "1234"}'
```
Response: (Note that the variables in the response depends on the workflow.)
```
{
  "workflowKey": 2251799813685249,
  "bpmnProcessId": "order-process",
  "version": 1,
  "workflowInstanceKey": 2251799813686045,
  "variables": "{\"orderId\":\"1234\"}"
}
```

  </p>
</details>

## Workflow instance creation by events

Workflow instances are also created implicitly via various start events. Zeebe supports message start events and timer start events.

### By publishing a message

A workflow with a [message start event](/bpmn-workflows/message-events/message-events.html#message-start-events) can be started by publishing a message with the  name that matches the message name of the start event.
For each new message a new instance is created.

### Using a timer

A workflow can also have one or more [timer start events](/bpmn-workflows/timer-events/timer-events.html#timer-start-events). An instance of the workflow is created when the associated timer is triggered.

## Distribution over partitions

 When a workflow instance is created in a partition, its state is stored and managed by the same partition until its execution is terminated. The partition in which it is created is determined by various factors.

- When a user sends a command `CreateWorkflowInstance` or `CreateWorkflowInstanceWithResult`, gateway chooses a partition in a round-robin manner and forwards the requests to that partition. The workflow instance is created in that partition.
- When a user publishes a message, the message is forwarded to a partition based on the correlation key of the message. The workflow instance is created on the same partition where the message is published.
- Workflow instances created by timer start events are always created on partition 1.
