# gRPC API Reference

* [Error handling](#error-handling)
* [Gateway service](#gateway-service)
  * [ActivateJobs RPC](#activatejobs-rpc)
  * [CancelWorkflowInstance RPC](#cancelworkflowinstance-rpc)
  * [CompleteJob RPC](#completejob-rpc)
  * [CreateWorkflowInstance RPC](#createworkflowinstance-rpc)
  * [DeployWorkflow RPC](#deployworkflow-rpc)
  * [FailJob RPC](#failjob-rpc)
  * [GetWorkflow RPC](#getworkflow-rpc)
  * [ListWorkflows RPC](#listworkflows-rpc)
  * [PublishMessage RPC](#publishmessage-rpc)
  * [ResolveIncident RPC](#resolveincident-rpc)
  * [Topology RPC](#topology-rpc)
  * [UpdateJobRetries RPC](#updatejobretries-rpc)
  * [UpdateWorkflowInstancePayload RPC](#updateworkflowinstancepayload-rpc)


## Error handling

The gRPC API for Zeebe is exposed through the gateway, which acts as a proxy
for the broker. Generally, this means that the client executes an remote call on the gateway,
which is then translated to special binary protocol that the gateway uses to
communicate with the broker.

As a result of this proxying, any errors which occur between the gateway and the broker
*for which the client is not at fault* (e.g. the gateway cannot deserialize the broker response,
the broker is unavailable, etc.) are reported to the client as internal errors
using the `GRPC_STATUS_INTERNAL` code. One exception to this is if the gateway itself is in
an invalid state (e.g. out of memory), at which point it will return `GRPC_STATUS_UNAVAILABLE`.

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

Iterates through all known partitions in a round-robin and activates up to the requested amount
of jobs and streams them back to the client as they are activated.

#### Input: ActivateJobsRequest

```protobuf
message ActivateJobsRequest {
  // the job type, as defined in the BPMN process (e.g. <zeebe:taskDefinition
  // type="payment-service" />)
  string type = 1;
  // the name of the worker activating the jobs, mostly used for logging purposes
  string worker = 2;
  // a job returned after this call will not be activated by another call until the
  // timeout has been reached
  int64 timeout = 3;
  // the maximum number of jobs to fetch in a single call
  int32 amount = 4;
  // a list of variables to fetch as the job payload; if empty, all visible variables at
  // the time of activation for the scope of the job will be returned as the job payload
  repeated string fetchVariable = 5;
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
  // a set of headers tying the job to a workflow instance/task instance
  JobHeaders jobHeaders = 3;
  // a set of custom headers defined during modelling; returned as a serialized
  // JSON document
  string customHeaders = 4;
  // the name of the worker which activated this job
  string worker = 5;
  // the amount of retries left to this job (should always be positive)
  int32 retries = 6;
  // when the job can be activated again, sent as a UNIX epoch timestamp
  int64 deadline = 7;
  // JSON document, computed at activation time, consisting of all visible variables to
  // the task scope
  string payload = 8;
}

message JobHeaders {
  // the job's workflow instance key
  int64 workflowInstanceKey = 1;
  // the bpmn process ID of the job workflow definition
  string bpmnProcessId = 2;
  // the version of the job workflow definition
  int32 workflowDefinitionVersion = 3;
  // the key of the job workflow definition
  int64 workflowKey = 4;
  // the associated task element ID
  string elementId = 5;
  // the unique key identifying the associated task, unique within the scope of the
  // workflow instance
  int64 elementInstanceKey = 6;
}
```

#### Errors

##### GRPC_STATUS_INVALID_ARGUMENT

Returned if:

  - type is blank (empty string, null)
  - worker is blank (empty string, null)
  - timeout less than 1
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
  string payload = 2;
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
  // valid payload, as the root of the JSON document is an array and not an object.
  string payload = 4;
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
  int64 workflowInstanceKey = 5;
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

  - the given payload is not a valid JSON document; all payloads are expected to be
    valid JSON documents where the root node is an object.


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


### GetWorkflow RPC

Fetches the workflow definition either by workflow key, or BPMN process ID and version.
At least one of `workflowKey` or `bpmnProcessId` must be specified.

#### Input: Request

```protobuf
message GetWorkflowRequest {
  // the unique key identifying the workflow definition (e.g. returned from a workflow in
  // the DeployWorkflowResponse message)
  int64 workflowKey = 1;
  // the version of the process; set to -1 to use the latest version
  int32 version = 2;
  // the BPMN process ID of the workflow definition
  string bpmnProcessId = 3;
}
```

#### Output: Response

```protobuf
message GetWorkflowResponse {
  // the unique key identifying the workflow definition (e.g. returned from a workflow in
  // the DeployWorkflowResponse message)
  int64 workflowKey = 1;
  // the version of the process
  int32 version = 2;
  // the BPMN process ID of the workflow definition
  string bpmnProcessId = 3;
  // the name of the resource used to deployed the workflow
  string resourceName = 4;
  // a BPMN XML representation of the workflow
  string bpmnXml = 5;
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no workflow with the given key exists (if workflowKey was given)
  - no workflow with the given process ID exists (if bpmnProcessId was given but version was -1)
  - no workflow with the given process ID and version exists (if both bpmnProcessId and version were given)

### ListWorkflows RPC

Lists all workflows matching the request criteria currently deployed in the cluster.

#### Input: Request

```protobuf
message ListWorkflowsRequest {
  // optional filter: if specified, only the workflows with this given process ID will be
  // returned
  string bpmnProcessId = 1;
}
```

#### Output: Response

```protobuf
message ListWorkflowsResponse {
  // a list of deployed workflows matching the request criteria (if any)
  repeated WorkflowMetadata workflows = 1;
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
  //parsed
  string resourceName = 4;
}
```

#### Errors

##### GRPC_STATUS_NOT_FOUND

Returned if:

  - no workflows have been deployed yet (if no bpmnProcessId was given)
  - no workflow with the given process ID exists (if bpmnProcessId was given)

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
  // the message payload as a JSON document; see CreateWorkflowInstanceRequest for the
  // rules about payloads
  string payload = 5;
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
  PartitionBrokerRole role = 3;
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


### UpdateWorkflowInstancePayload RPC

Updates all the variables in the workflow instance scope from the given JSON document.

#### Input: Request

```protobuf
message UpdateWorkflowInstancePayloadRequest {
  // the unique identifier of a particular element; can be the workflow instance key (as
  // obtained during instance creation), or a given element, such as a service task (see
  // elementInstanceKey on the JobHeaders message)
  int64 elementInstanceKey = 1;
  // the new payload as a JSON document; see CreateWorkflowInstanceRequest for the rules
  // about payloads
  string payload = 2;
}
```

#### Output: Response

```protobuf
message UpdateWorkflowInstancePayloadResponse {
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
