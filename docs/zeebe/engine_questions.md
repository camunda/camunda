# Engine Questions

This document contains information about the engine in a FAQ format.

## What are tokens and how are they represented in the database?

According to the spec, tokens move through the flow. This is a way that users can think of how the
engine executes the process. But in reality this is not how the engine does it. There is no token
entity moving through the engine. Tokens are also not spawned and consumed for elements.

**A:** Tokens are represented in the engine and in its database state in one of two ways:
* Activated elements
* Taken sequence flows

## How does the StreamPlatform and Engine interaction look like?

Open the model and start the token flow to see the interaction between StreamProcessor (maintained by ZDP) and the RecordProcessor (Engine, maintained by the ZPA).

[![engine-bpmn](https://user-images.githubusercontent.com/2758593/195849695-e0c0ee19-9557-48ad-89f0-0bcccc845bbd.png)](https://modeler.cloud.ultrawombat.com/diagrams/32c22fc0-709a-48d7-afe5-83a0c40a83fd--engine-bpmn?v=1076,651,1)

### Activated Elements

The engine tracks the state of element instances. If they are in the state _Activated_ this is
visualized as a token in Operate.

In case of multi instance elements, each instance is activated individually. Operate aggregates this information
and displays a count of tokens/activated element instances for the multi instance element.

### Taken Sequence Flows

The engine also tracks taken sequence flows - more precisely it tracks taken sequence flows which
were unable to activate the element at the end of the sequence flow. This is mostly relevant for
joining parallel gateways. These have multiple incoming sequence flows.

When the first sequence flow is taken, the engine analyzes all taken sequence flows and determines
that the joining parallel gateway cannot be activated yet. Instead, it stores the taken sequence
flow as part of the event scope of the parent element of the joining parallel gateway. This taken
sequence flow is also visualized as a token in Operate.

Once all incoming sequence flows of a joining parallel gateway are taken, the information of the
taken sequence flows is removed from the event scope and the joining parallel gateway is activated.
In the visualization in Operate this looks as if the tokens were merged/removed on the joining
parallel gateway.

### Background information:

* Camunda Platform 7 also works this way and does not actually know tokens.
* The database has a representation of the current state of a process instance because it stores the
  element instances that are currently active (activating, activated, completing and terminating), as
  well as the number of active sequence flows inside of a flow scope.
* This information is used to determine whether the scope can be completed or terminated. When one
  of the instances completes or terminates, it's possible that other instances are still active and
  the scope cannot be completed/terminated yet.
* The engine used to do reference counting with the number of active tokens. These were not really
  token entities, but rather just a number that was incremented when a token was spawned and
  decremented when the token was consumed. However, this often led to problems where the count was
  not correct anymore.
* Representing the state of a process with tokens also raises interesting questions related to the
  scoping of data, because it might make sense to scope the data then to the token. This leads to
  interesting challenges once the flow splits and additional tokens are spawned, for example with
  parallel gateway. And what happens to the data when the tokens merge at the joining parallel gateway.

## What is the cardinality between a process instance and the element instances of a given element id (1:1 or 1:n)?

In particular, in loops does each entry into an element create a new element instance, or are all
passing through the same element instance?

**A:** The same element can be activated multiple times within the same process instance. So the
cardinality is 1:n between the process instance and its element instances.

Another way to look at it is by looking at the records on the log. We represent instances of process
elements in the log as `ProcessInstance` records. Each instance of an element has its own unique key
and has an elementId that refers to the element that it is an instance of. The element instance also
has a `processInstanceKey` that refers to the process instance that it belongs to. An element
instance belongs to an element and belongs to a process instance. Multiple element instances can
exist for the same element, even within the same process instance.

## When is a joining parallel gateway activated?

![Example of joining parallel gatewway](assets/joining_parallel_gateway.png)

This process creates 4 tokens at different times. In the upper branch it creates a token at 10s and
20s, and in the lower branch it creates tokens at 1m and 2m respectively. The process was crafted in
this way to ask when will the joining gateway _Join_ be activated:
1. When two tokens (regardless of edge/sequence flow) are present - then it would activate at 20s and at 2m
2. When a token is present at each incoming edge/sequence flow - then it would activate at 1m and 2m

**A:** The joining parallel gateway _Join_ is activated at 1m and 2m

## Is there an internal cached representation of the process model, or do we parse the process document each time we need to access its structure?

**A:** The engine has an in-memory cache for deployed/recently accessed models in the executable
format we need for executing the process.

_Note:_ The cache is not evicting any entries right now which can in theory lead to out of memory errors.

## How does deployment distribution work?

**A:**
![Deployment distribution](assets/deployment_distribution.png)

## How are variables from different nested flow scopes collected before e.g. a service task is called?

**A:**
When a service tasks is activated the variables are collected by traversing the flow scopes from the element to the root scope of the current process, variables are added to the map of variables if the variable key is not already part of the variables map. No variables from parent processes are collected during the traversal, unless they are mapped into the current process.

When a worker specifies a list of variable names to fetch we only add variables into the collected map if their name is part of the fetch variables list, and stop once all variables are found or the root scope of the current process is found. If fetch variable contains a name of a variable which does not exist in the process then the job activation response does not contain this variable (i.e. the requested variable is ignored)

## After a service task was called (job completed), how are the variables returned by it redistributed/propagated into the nested flow scopes they came from?

**A:**

If _no output mapping_ exists: For the payload, we look at each scope (from innermost flow scope to root scope of process) whether a variable with that name exists or not. If so, it can update the variable, otherwise it continues up another level until it reaches the current root process scope. If no variable is found there, it is created new there.

If _an output mapping_ exists, we apply the output mapping over the payload first, and then look at each scope whether a variable with that name exists or not. If so, it can update the variable with the new value otherwise it continues up another level until it reaches the root process scope. If the root process scope also does not contain a variable with that name, then the variable is created new at the root process scope.
Any variables in the payload that are not part of the output mappings will be created in the service task flow scope.
