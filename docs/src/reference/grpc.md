# gRPC API Reference

* [Error handling](#error-handling)
* [Gateway service](#gateway-service)
  * [ActivateJobs RPC](#activatejobs-rpc)
  * [CancelWorkflowInstance RPC](#cancelworkflowinstance-rpc)
  * [CompleteJob RPC](#completejob-rpc)
  * [CreateWorkflowInstance RPC](#createworkflowinstance-rpc)
  * [CreateWorkflowInstanceWithResult RPC](#createworkflowinstance-rpc)
  * [DeployWorkflow RPC](#deployworkflow-rpc)
  * [FailJob RPC](#failjob-rpc)
  * [PublishMessage RPC](#publishmessage-rpc)
  * [ResolveIncident RPC](#resolveincident-rpc)
  * [SetVariables RPC](#setvariables-rpc)
  * [ThrowError RPC](#throwerror-rpc)
  * [Topology RPC](#topology-rpc)
  * [UpdateJobRetries RPC](#updatejobretries-rpc)

## Error handling

The gRPC API for Zeebe is exposed through the gateway, which acts as a proxy
for the broker. Generally, this means that the client executes an remote call on the gateway,
which is then translated to special binary protocol that the gateway uses to
communicate with the broker.

As a result of this proxying, any errors which occur between the gateway and the broker
*for which the client is not at fault* (e.g. the gateway cannot deserialize the broker response,
the broker is unavailable, etc.) are reported to the client using the following error codes.
 * `GRPC_STATUS_RESOURCE_EXHAUSTED`: if the broker is receiving too many requests more than what it can handle, it kicks off back-pressure and rejects requests with this error code.
    In this case, it is possible to retry the requests with an appropriate retry strategy.
    If you receive many such errors with in a small time period, it indicates that the broker is constantly under high load.
    It is recommended to reduce the rate of requests.
    When the back-pressure kicks off, the broker may reject any request except *CompleteJob* RPC and *FailJob* RPC.
    These requests are white-listed for back-pressure and are always accepted by the broker even if it is receiving requests above its limits.
 * `GRPC_STATUS_UNAVAILABLE`:  if the gateway itself is in an invalid state (e.g. out of memory)
 * `GRPC_STATUS_INTERNAL`:  for any other internal errors that occurred between the gateway and the broker.

This behavior applies to every single possible RPC; in these cases, it is possible that retrying
would succeed, but it is recommended to do so with an appropriate retry policy
(e.g. a combination of exponential backoff or jitter wrapped in a circuit breaker).


In the documentation below, the documented errors are business logic errors, meaning
errors which are a result of request processing logic, and not serialization, network, or
other more general errors.

> As the gRPC server/client is based on generated code, keep in mind that
any call made to the server can return errors as described by the spec
[here](https://grpc.io/docs/guides/error.html#error-status-codes).


## Gateway service

The Zeebe gRPC API is exposed through a single gateway service.

### ActivateJobs RPC
Iterates through all known partitions round-robin and activates up to the requested
maximum and streams them back to the client as they are activated.

#### Input: ActivateJobsRequest

```protobuf
message ActivateJobsRequest {
  // the job type, as defined in the BPMN process (e.g. <zeebe:taskDefinition
  // type="payment-service" />)
  string type = 1;
  // the name of the worker activating the jobs, mostly used for logging purposes
  string worker = 2;
  // a job returned after this call will not be activated by another call until the
  // timeout (in ms) has been reached
  int64 timeout = 3;
  // the maximum jobs to activate by this request
  int32 maxJobsToActivate = 4;
  // a list of variables to fetch as the job variables; if empty, all visible variables at
  // the time of activation for the scope of the job will be returned
  repeated string fetchVariable = 5;
  // The request will be completed when at least one job is activated or after the requestTimeout (in ms).
  // if the requestTimeout = 0, a default timeout is used.
  // if the requestTimeout < 0, long polling is disabled and the request is completed immediately, even when no job is activated.
  int64 requestTimeout = 6;
}
```

#### Output: ActivateJobsResponse

```protobuf
message ActivateJobsResponse {
  // list of activated jobs
  repeated ActivatedJob jobs = 1;
}

message ActivatedJob {
  // the key, a unique identifier for the job
  int64 key = 1;
  // the type of the job (should match what was requested)
  string type = 2;
  // the job's workflow instance key
  int64 workflowInstanceKey = 3;
  // the bpmn process ID of the job workflow definition
  string bpmnProcessId = 4;
  // the version of the job workflow definition
  int32 workflowDefinitionVersion = 5;
  // the key of the job workflow definition
  int64 workflowKey = 6;
  // the associated task element ID
  string elementId = 7;
  // the unique key identifying the associated task, unique within the scope of the
  // workflow instance
  int64 elementInstanceKey = 8;
  // a set of custom headers defined during modelling; returned as a serialized
  // JSON document
  string customHeaders = 9;
  // the name of the worker which activated this job
  string worker = 10;
  // the amount of retries left to this job (should always be positive)
  int32 retries = 11;
  // when the job can be activated again, sent as a UNIX epoch timestamp
  int64 deadline = 12;
  // JSON document, computed at activation time, consisting of all visible variables to
  // the task scope
  string variables = 13;
}
```

#### Errors

##### GRPC_STATUS_INVALID_ARGUMENT

Returned if:

  - type is blank (empty string, null)
  - worker is blank (empty string, null)
  - timeout less than 1 (ms)
  - amount is less than 1


### CancelWorkflowInstance RPC

Cancels a running workflow instance

#### Input: CancelWorkflowInstanceRequest

```protobuf
message CancelWorkflowInstanceRequest {
  // the workflow instance key (as, for example, obtained from
  // CreateWorkflowInstanceResponse)
  int64 workflowInstanceKey = 1;
}
```

#### Output: CancelWorkflowInstanceResponse

```protobuf
message CancelWorkflowInstanceResponse {
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no workflow instance exists with the given key. Note that since workflow instances
    are removed once their are finished, it could mean the instance did exist at some point.


### CompleteJob RPC

Completes a job with the given payload, which allows completing the associated service task.

#### Input: CompleteJobRequest

```protobuf
message CompleteJobRequest {
  // the unique job identifier, as obtained from ActivateJobsResponse
  int64 jobKey = 1;
  // a JSON document representing the variables in the current task scope
  string variables = 2;
}
```

#### Output: CompleteJobResponse

```protobuf
message CompleteJobResponse {
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no job exists with the given job key. Note that since jobs are removed once completed,
    it could be that this job did exist at some point.

##### GRPC_STATUS_FAILED_PRECONDITION

Returned if:

  - the job was marked as failed. In that case, the related incident must be resolved before
    the job can be activated again and completed.


### CreateWorkflowInstance RPC

Creates and starts an instance of the specified workflow. The workflow definition to use
to create the instance can be specified either using its unique key (as returned by
DeployWorkflow), or using the BPMN process ID and a version. Pass -1 as the version to
use the latest deployed version.

Note that only workflows with none start events can be started through this command.

#### Input: CreateWorkflowInstanceRequest

```protobuf
message CreateWorkflowInstanceRequest {
  // the unique key identifying the workflow definition (e.g. returned from a workflow
  // in the DeployWorkflowResponse message)
  int64 workflowKey = 1;
  // the BPMN process ID of the workflow definition
  string bpmnProcessId = 2;
  // the version of the process; set to -1 to use the latest version
  int32 version = 3;
  // JSON document that will instantiate the variables for the root variable scope of the
  // workflow instance; it must be a JSON object, as variables will be mapped in a
  // key-value fashion. e.g. { "a": 1, "b": 2 } will create two variables, named "a" and
  // "b" respectively, with their associated values. [{ "a": 1, "b": 2 }] would not be a
  // valid argument, as the root of the JSON document is an array and not an object.
  string variables = 4;
}
```

#### Output: CreateWorkflowInstanceResponse

```protobuf
message CreateWorkflowInstanceResponse {
  // the key of the workflow definition which was used to create the workflow instance
  int64 workflowKey = 1;
  // the BPMN process ID of the workflow definition which was used to create the workflow
  // instance
  string bpmnProcessId = 2;
  // the version of the workflow definition which was used to create the workflow instance
  int32 version = 3;
  // the unique identifier of the created workflow instance; to be used wherever a request
  // needs a workflow instance key (e.g. CancelWorkflowInstanceRequest)
  int64 workflowInstanceKey = 4;
}
```

### CreateWorkflowInstanceWithResult RPC

Similar to `CreateWorkflowInstance RPC` , creates and starts an instance of the specified workflow.
Unlike `CreateWorkflowInstance RPC`, the response is returned when the workflow is completed.

Note that only workflows with none start events can be started through this command.

#### Input: CreateWorkflowInstanceWithResultRequest

```protobuf
message CreateWorkflowInstanceRequest {
   CreateWorkflowInstanceRequest request = 1;
   // timeout (in ms). the request will be closed if the workflow is not completed before
   // the requestTimeout.
   // if requestTimeout = 0, uses the generic requestTimeout configured in the gateway.
   int64 requestTimeout = 2;
}
```

#### Output: CreateWorkflowInstanceWithResultResponse

```protobuf
message CreateWorkflowInstanceResponse {
  // the key of the workflow definition which was used to create the workflow instance
  int64 workflowKey = 1;
  // the BPMN process ID of the workflow definition which was used to create the workflow
  // instance
  string bpmnProcessId = 2;
  // the version of the workflow definition which was used to create the workflow instance
  int32 version = 3;
  // the unique identifier of the created workflow instance; to be used wherever a request
  // needs a workflow instance key (e.g. CancelWorkflowInstanceRequest)
  int64 workflowInstanceKey = 4;
  // consisting of all visible variables to the root scope
  string variables = 5;
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no workflow with the given key exists (if workflowKey was given)
  - no workflow with the given process ID exists (if bpmnProcessId was given but version was -1)
  - no workflow with the given process ID and version exists (if both bpmnProcessId and version were given)

##### GRPC_STATUS_FAILED_PRECONDITION

Returned if:

  - the workflow definition does not contain a none start event; only workflows with none
    start event can be started manually.

##### GRPC_STATUS_INVALID_ARGUMENT

Returned if:

  - the given variables argument is not a valid JSON document; it is expected to be a valid
    JSON document where the root node is an object.


### DeployWorkflow RPC

Deploys one or more workflows to Zeebe. Note that this is an atomic call,
i.e. either all workflows are deployed, or none of them are.

#### Input: DeployWorkflowRequest

```protobuf
message DeployWorkflowRequest {
  // List of workflow resources to deploy
  repeated WorkflowRequestObject workflows = 1;
}

message WorkflowRequestObject {
  enum ResourceType {
    // FILE type means the gateway will try to detect the resource type
    // using the file extension of the name field
    FILE = 0;
    BPMN = 1; // extension 'bpmn'
    YAML = 2; // extension 'yaml'
  }

  // the resource basename, e.g. myProcess.bpmn
  string name = 1;
  // the resource type; if set to BPMN or YAML then the file extension
  // is ignored
  ResourceType type = 2;
  // the process definition as a UTF8-encoded string
  bytes definition = 3;
}
```

#### Output: DeployWorkflowResponse

```protobuf
message DeployWorkflowResponse {
  // the unique key identifying the deployment
  int64 key = 1;
  // a list of deployed workflows
  repeated WorkflowMetadata workflows = 2;
}

message WorkflowMetadata {
  // the bpmn process ID, as parsed during deployment; together with the version forms a
  // unique identifier for a specific workflow definition
  string bpmnProcessId = 1;
  // the assigned process version
  int32 version = 2;
  // the assigned key, which acts as a unique identifier for this workflow
  int64 workflowKey = 3;
  // the resource name (see: WorkflowRequestObject.name) from which this workflow was
  // parsed
  string resourceName = 4;
}
```

#### Errors

##### GRPC_STATUS_INVALID_ARGUMENT

Returned if:

  - no resources given.
  - if at least one resource is invalid. A resource is considered invalid if:
    * it is not a BPMN or YAML file (currently detected through the file extension)
    * the resource data is not deserializable (e.g. detected as BPMN, but it's broken XML)
    * the workflow is invalid (e.g. an event-based gateway has an outgoing sequence flow to a task)


### FailJob RPC

Marks the job as failed; if the retries argument is positive, then the job will be immediately
activatable again, and a worker could try again to process it. If it is zero or negative however,
an incident will be raised, tagged with the given errorMessage, and the job will not be
activatable until the incident is resolved.

#### Input: FailJobRequest

```protobuf
message FailJobRequest {
  // the unique job identifier, as obtained when activating the job
  int64 jobKey = 1;
  // the amount of retries the job should have left
  int32 retries = 2;
  // an optional message describing why the job failed
  // this is particularly useful if a job runs out of retries and an incident is raised,
  // as it this message can help explain why an incident was raised
  string errorMessage = 3;
}
```

#### Output: FailJobResponse

```protobuf
message FailJobResponse {
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no job was found with the given key

##### GRPC_STATUS_FAILED_PRECONDITION

Returned if:

  - the job was not activated
  - the job is already in a failed state, i.e. ran out of retries

### PublishMessage RPC

Publishes a single message. Messages are published to specific partitions computed from their
correlation keys.

#### Input: Request

```protobuf
message PublishMessageRequest {
  // the name of the message
  string name = 1;
  // the correlation key of the message
  string correlationKey = 2;
  // how long the message should be buffered on the broker, in milliseconds
  int64 timeToLive = 3;
  // the unique ID of the message; can be omitted. only useful to ensure only one message
  // with the given ID will ever be published (during its lifetime)
  string messageId = 4;
  // the message variables as a JSON document; to be valid, the root of the document must be an
  // object, e.g. { "a": "foo" }. [ "foo" ] would not be valid.
  string variables = 5;
}
```

#### Output: Response

```protobuf
message PublishMessageResponse {
}
```

#### Errors

##### GRPC_STATUS_ALREADY_EXISTS

Returned if:

  - a message with the same ID was previously published (and is still alive)


### ResolveIncident RPC

Resolves a given incident. This simply marks the incident as resolved; most likely a call to
UpdateJobRetries or UpdateWorkflowInstancePayload will be necessary to actually resolve the
problem, following by this call.

#### Input: Request

```protobuf
message ResolveIncidentRequest {
  // the unique ID of the incident to resolve
  int64 incidentKey = 1;
}
```

#### Output: Response

```protobuf
message ResolveIncidentResponse {
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no incident with the given key exists


### SetVariables RPC

Updates all the variables of a particular scope (e.g. workflow instance, flow element instance) from the given JSON document.

#### Input: Request

```protobuf
message SetVariablesRequest {
  // the unique identifier of a particular element; can be the workflow instance key (as
  // obtained during instance creation), or a given element, such as a service task (see
  // elementInstanceKey on the job message)
  int64 elementInstanceKey = 1;
  // a JSON serialized document describing variables as key value pairs; the root of the document
  // must be an object
  string variables = 2;
  // if true, the variables will be merged strictly into the local scope (as indicated by
  // elementInstanceKey); this means the variables is not propagated to upper scopes.
  // for example, let's say we have two scopes, '1' and '2', with each having effective variables as:
  // 1 => `{ "foo" : 2 }`, and 2 => `{ "bar" : 1 }`. if we send an update request with
  // elementInstanceKey = 2, variables `{ "foo" : 5 }`, and local is true, then scope 1 will
  // be unchanged, and scope 2 will now be `{ "bar" : 1, "foo" 5 }`. if local was false, however,
  // then scope 1 would be `{ "foo": 5 }`, and scope 2 would be `{ "bar" : 1 }`.
  bool local = 3;
}
```

#### Output: Response

```protobuf
message SetVariablesResponse {
  // the unique key of the set variables command
  int64 key = 1;
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no element with the given `elementInstanceKey` was exists

##### GRPC_STATUS_INVALID_ARGUMENT

Returned if:

  - the given payload is not a valid JSON document; all payloads are expected to be
    valid JSON documents where the root node is an object.


### ThrowError RPC

Throw an error to indicate that a business error is occurred while processing the job. The error is identified by an error code and is handled by an error catch event in the workflow with the same error code.

#### Input: ThrowErrorRequest

```protobuf
message ThrowErrorRequest {
  // the unique job identifier, as obtained when activating the job
  int64 jobKey = 1;
  // the error code that will be matched with an error catch event
  string errorCode = 2;
  // an optional error message that provides additional context
  string errorMessage = 3;
}
```

#### Output: ThrowErrorResponse

```protobuf
message ThrowErrorResponse {
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no job was found with the given key

##### GRPC_STATUS_FAILED_PRECONDITION

Returned if:

  - the job is already in a failed state, i.e. ran out of retries

### Topology RPC

Obtains the current topology of the cluster the gateway is part of.

#### Input: TopologyRequest

```protobuf
message TopologyRequest {
}
```

#### Output: TopologyResponse

```protobuf
message TopologyResponse {
  // list of brokers part of this cluster
  repeated BrokerInfo brokers = 1;
  // how many nodes are in the cluster
  int32 clusterSize = 2;
  // how many partitions are spread across the cluster
  int32 partitionsCount = 3;
  // configured replication factor for this cluster
  int32 replicationFactor = 4;
  // gateway version
  string gatewayVersion = 5;
}

message BrokerInfo {
  // unique (within a cluster) node ID for the broker
  int32 nodeId = 1;
  // hostname of the broker
  string host = 2;
  // port for the broker
  int32 port = 3;
  // list of partitions managed or replicated on this broker
  repeated Partition partitions = 4;
  // broker version
  string version = 5;
}

message Partition {
  // Describes the Raft role of the broker for a given partition
  enum PartitionBrokerRole {
    LEADER = 0;
    FOLLOWER = 1;
  }

  // the unique ID of this partition
  int32 partitionId = 1;
  // the role of the broker for this partition
  PartitionBrokerRole role = 2;
}
```

#### Errors

No specific errors

### UpdateJobRetries RPC

Updates the number of retries a job has left. This is mostly useful for jobs that have run out of
retries, should the underlying problem be solved.

#### Input: Request

```protobuf
message UpdateJobRetriesRequest {
  // the unique job identifier, as obtained through ActivateJobs
  int64 jobKey = 1;
  // the new amount of retries for the job; must be positive
  int32 retries = 2;
}
```

#### Output: Response

```protobuf
message UpdateJobRetriesResponse {
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no job exists with the given key

##### GRPC_STATUS_INVALID_ARGUMENT

Returned if:

  - retries is not greater than 0
