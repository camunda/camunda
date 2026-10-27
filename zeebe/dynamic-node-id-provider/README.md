# Dynamic Node ID Provider

## Purpose

The purpose of this module is to provide an implementation of dynamic node ID assignment,
to be used only when stable node identifiers are not provided to the application statically (e.g., StatefulSet in Kubernetes).

Cloud services for stateless containers such as AWS Elastic Container Service (ECS) are examples of such environments.

## Interface

Other modules can interact with this module via the [NodeIdProvider](./src/main/java/io/camunda/zeebe/dynamic/nodeid/NodeIdProvider.java).

## Implementation

The current implementation requires a CAS (Compare-And-Swap) capable file storage implementation such as S3.
The atomic property of the file storage is the concurrency primitive that is used to ensure consistency in the node ID selection algorithm
when multiple nodes are trying to select the same node ID.

In the file storage, each node acquires a "lease" that is valid for some time (on the order of 5-15 seconds).
After that time, the lease is considered expired and can be acquired by other nodes.

Nodes need to continuously "renew" the acquired lease to avoid losing it.
If a node fails to renew the lease, it needs to shut down the process to avoid data corruption.

### Detailed implementation

#### Cluster Bootstrap

- When the cluster starts, the S3 bucket is empty.
- Each task tries to create lease objects for node IDs from 0 up to `clusterSize - 1`. All tasks do this at the same time.
- Updates use Compare-And-Swap (CAS), so concurrent initialization is safe.
- After this, the S3 bucket will contain objects like:
  - `0.json`
  - `1.json`
  - ...
  - `${clusterSize-1}.json`
- Each object represents an unacquired lease for a node ID.

#### On Every (Re)start

- Each task tries to acquire a lease for a node ID by checking each lease object from 0 to `clusterSize-1`.
- The first available node ID is acquired using CAS.
- A node ID is available if:
- No one holds the lease (the previous holder released it), or
- The lease timed out (the previous holder didn’t release it, possibly due to a crash or network issue).
- When a lease is acquired, set `nodeIdVersion = previousVersion + 1`.
- After acquiring a lease, the task schedules regular lease renewals.
  - On renewal, update the timestamp in the lease object:
    - `timestamp = now() + lease expiry duration`
  - If the lease cannot be renewed, the node should shut down immediately for safety.
- The lease object contains:
  - `taskId` (current lease holder)
  - `version`
  - `timestamp `(expiry time)
  - `knownNodesMapping:` a map of `nodeId -> version `that this node knows about (via SWIM).
- On each renewal, update the timestamp and, if needed, the mapping.
- On shutdown, release the lease by:
  - Clearing the lease object content (empty body)
  - Clearing the metadata
- The broker's health check depends on the `NodeIdProvider`. If the lease isn't renewed, the health check fails and the infrastructure should terminate the node.

