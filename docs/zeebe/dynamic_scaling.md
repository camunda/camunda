# Dynamic Scaling

*Dynamic Scaling* refers to the process of increasing the number of partitions of a zeebe cluster once a cluster has been created.
Before this feature, the number of partitions could not be changed.

The scaling process is comprised of two sequential phases that ensure new partitions are fully integrated into the cluster:

1. **Redistribution**: Deploy common resources (process definitions, decisions, forms) to new partitions so they can process events
2. **Relocation**: Redistribute message/signal subscriptions across all partitions (old and new) for optimal correlation

## Redistribution

A sequence diagram of the redistribution process can be found below:

```mermaid
sequenceDiagram
autonumber
participant CoordinatorNode
participant P-1
participant P-1-Snapshot
participant P-ith
participant P-NEW

CoordinatorNode ->>+ CoordinatorNode: Add ClusterChangeOperations to ClusterPlan

CoordinatorNode ->> P-1: Send SCALE_UP (w/ desiredPartitionCount)
P-1 ->> P-1: [SCALING_UP] Add desiredPartitionCount to RoutingInfo: <br> new partitions are included in distribution (only enqueued)

CoordinatorNode ->>+ P-NEW: Bootstrap new partition from snapshot of P1
P-NEW ->> P-1: Get snapshot for bootstrap
P-1 ->> P-1: Take a snapshot such that processedPosition > position of SCALING_UP event

CoordinatorNode ->> CoordinatorNode: AwaitRedistributionCompletion

CoordinatorNode ->> P-1: Redistribution completed?
P-1 ->> CoordinatorNode: No

P-1 ->> P-NEW: Snapshot for bootstrap with SCALING_UP event
P-NEW ->>- P-1: Partition NEW Bootstrapped
P-1 ->> P-1: Start distributing pending commands

loop for i in distribution_commands(scaling_up_idx, last_log_idx)
  P-1 ->> P-NEW: send distribution command at i
end

P-1 ->> P-2: Partition i Bootstrapped
CoordinatorNode ->> P-1: Redistribution completed?
P-1 ->> CoordinatorNode: Yes
CoordinatorNode ->>- CoordinatorNode : Update RoutingState.RequestHandling

P-1 ->> P-ith: Distribution event at b_e + n
P-1 ->> P-NEW: Distribution event at b_e + n
```

### Detailed steps

1. A client sends a [ClusterConfigurationManagementRequest](zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/api/ClusterConfigurationManagementApi.java) as a PATCH HTTP request at `actuator/cluster/`
1. The [ClusterChangeCoordinator](zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/changes/ConfigurationChangeCoordinator.java) generates the `ClusterChangeOperation` needed to reach the requested result.
  For a pure scaling operation, the generated `ClusterChangeOperation` will be:
    - `StartPartitionScaleUp` to Partition 1
    - `PartitionBootstrapOperation` to all new partitions
    - `AwaitRedistributionCompletion` to the coordinator node 1

 Once generated, the coordinator node will execute one operation at a time, on the correct node.

1. `StartPartitionScaleUp`:
    - Partition 1 marks that the scale up is started in the state.
    - new partitions are added to the `desiredPartitionCount` in the state
    - commands distribution will enqueue new commands for the new partitions, but it will not start distributing them yet.

1. `PartitionBootstrapOperation` on the new partitions (one partition at a time, from the *lowest* to the *highest*):

    - The partition is bootstrapped in the node with the flag `bootstrapFromSnapshot=true`.
    - It will request the snapshot for bootstrap to partition 1.

    - When the snapshot is received, it will restore the `PersistedSnapshotStore` with it, before the partition is even started (in a `PartitionStartupStep`)
    - When the bootstrap is completed, it will inform partition 1 that they are ready to process messages
    - Partition 1 will include the newly bootstrapped partitions to the `activePartitions` field in `RoutingInfo` in the state
    - Partition 1 will distribute to all partitions the newly received bootstrap command
    - All other partition will do the same changes to the `RoutingInfo` in the state

1. `AwaitRedistributionCompletion`:
   CoordinatorNode will keep polling Partition 1 to know when all partitions have been bootstrapped.

   When redistribution is completed, the routing information in the dynamic config module will update, so that gateways will send new requests to the new partitions
