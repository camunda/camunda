# Dynamic Scaling

*Dynamic Scaling* refers to the process of increasing the number of partitions of a zeebe cluster once a cluster has been created.
Before this feature, the number of partitions could not be changed.

> [!NOTE]
> Dynamic scaling is currently a **beta feature**. The redistribution phase is fully implemented and stable, while the relocation phase (subscription migration) is not yet implemented and is planned for future releases.

The scaling process is comprised of two sequential phases that ensure new partitions are fully integrated into the cluster:

1. **Redistribution**: Deploy common resources (process definitions, decisions, forms) to new partitions so they can process events
2. **Relocation**: Redistribute message/signal subscriptions across all partitions (old and new) for even performance when correlating messages *(currently not implemented)*

## Redistribution

The redistribution phase ensures that new partitions receive all the necessary global resources (process definitions, decisions, forms) required to process workflow instances. This phase involves taking a snapshot of global state from partition 1 and distributing it to newly created partitions.

### What Gets Redistributed

During redistribution, the following resources are automatically deployed to new partitions:
- **Process Definitions**: All deployed BPMN processes
- **Decision Definitions**: DMN decision tables and DRD definitions  
- **Form Definitions**: forms linked to user tasks
- **Global Configuration**: Routing state and partition configuration

**Important**: Partition-specific state (active process instances, jobs, message subscriptions) remains on the original partitions and is **not** redistributed.

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

loop for command in enqueued_distribution_commands
  P-1 ->> P-NEW: send command 
end

P-1 ->> P-2: Partition i Bootstrapped
CoordinatorNode ->> P-1: Redistribution completed?
P-1 ->> CoordinatorNode: Yes
CoordinatorNode ->>- CoordinatorNode : Update RoutingState.RequestHandling

P-1 ->> P-ith: Distribution event at b_e + n
P-1 ->> P-NEW: Distribution event at b_e + n
```

### Command Distribution Mechanism

The redistribution phase uses [Zeebe's command distribution](./generalized_distribution.md) system to ensure reliable deployment of resources across partitions:

1. **Command Enqueueing**: When scaling begins, deployment commands for new partitions are enqueued but not immediately sent
2. **Bootstrap Completion Signal**: Each new partition signals completion of bootstrap to partition 1  
3. **Distributed Deployment**: When a partition is bootstrapped, partition 1 distributes queued deployment commands to the partitions (including existing ones)
4. **Acknowledgment**: Receiving partitions acknowledge successful processing of deployment commands
5. **Retry Logic**: Failed distributions are automatically retried with exponential backoff

### Scaling steps

1. A client sends a [ClusterConfigurationManagementRequest](../../zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/api/ClusterConfigurationManagementApi.java) as a HTTP PATCH request at `/actuator/cluster/` on the management port.
 
    If new brokers need to be added, it can be done in the same command, by specifying the ids of the brokers to add:
      - with brokers:
          ```bash
          curl -X 'PATCH' \
            'http://localhost:9600/actuator/cluster' \
            -H 'accept: application/json' \
            -H 'Content-Type: application/json' \
            -d '{  
                  "brokers": { 
                    "add": [3,4,5]
                  },
                  "partitions": {
                    "count": 6, 
                    "replicationFactor": 3
                  }
                }' | jq
          ```
      - without brokers
          ```bash
          curl -X 'PATCH' \
            'http://localhost:9600/actuator/cluster' \
            -H 'accept: application/json' \
            -H 'Content-Type: application/json' \
            -d '{  
                  "brokers": { 
                    "add": [3,4,5]
                  },
                  "partitions": {
                    "count": 6, 
                    "replicationFactor": 3
                  }
                }' | 
          ```


1. The [ClusterChangeCoordinator](../../zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/changes/ConfigurationChangeCoordinator.java) generates the `ClusterChangeOperation` needed to reach the requested result.
  For a pure scaling operation, the generated `ClusterChangeOperation` will be:
    - `StartPartitionScaleUp` to Partition 1
    - `PartitionBootstrapOperation` to all new partitions
    - `AwaitRedistributionCompletion` to the coordinator node 1

    Once generated, the coordinator node will execute one operation at a time, on the correct node.

    **Partition ID Requirements**: New partitions must have contiguous IDs (e.g., if you currently have partitions 1-3, you can only scale to 4, 5, 6, etc.). Non-contiguous partition IDs are rejected to maintain compatibility with existing routing logic.

1. `StartPartitionScaleUp`:
    - Partition 1 marks that the scale up is started in the state.
    - new partitions are added to the `desiredPartitionCount` in the state
    - commands distribution will enqueue new commands for the new partitions, but it will not start distributing them yet.

1. `PartitionBootstrapOperation` on the new partitions (one partition at a time, from the *lowest* to the *highest*):

    - The partition is bootstrapped in the node with the flag `bootstrapFromSnapshot=true`.
    - It will request the snapshot for bootstrap to partition 1.

    - When the snapshot is received, it will restore the `PersistedSnapshotStore` with it, before the partition is even started (in a `PartitionStartupStep`)
    - When the bootstrap is completed, it will inform partition 1 that they are ready to start new process instances 
    - Partition 1 will include the newly bootstrapped partitions to the `activePartitions` field in `RoutingInfo` in the state
    - Partition 1 will start distributing enqueue distribution commands to the new partition
    - Partition 1 will distribute to all partitions the newly received bootstrap command
    - All other partition will do the same changes to the `RoutingInfo` in the state

    **Bootstrap Validation**: Each partition validates the received snapshot before marking itself as bootstrapped. If validation fails, the operation is retried automatically.

1. `AwaitRedistributionCompletion`:
   `CoordinatorNode` will keep polling Partition 1 to know when all partitions have been bootstrapped.

   When redistribution is completed, the routing information in the dynamic config module will update, so that gateways will deploy new process instances to the newly bootstrapped partitions as well.

## Relocation

The relocation phase is designed to redistribute message and signal subscriptions across all partitions (both old and new) for optimal correlation performance. 

> [!IMPORTANT]
> **Current Status**: The relocation phase is **not yet implemented**. The `AwaitRelocationCompletion` operation is currently a no-op that immediately completes without performing any subscription migration.

## Scaling Event Flow inside the engine

The scaling process follows a sequence of intents and events that ensure data consistency and proper partition activation:

### Intent Sequence
1. **SCALE_UP** → **SCALING_UP**: Initiates scaling and updates routing state to include desired partitions
2. **MARK_PARTITION_BOOTSTRAPPED** → **PARTITION_BOOTSTRAPPED**: Marks each new partition as ready after bootstrap and includes them in the active partitions (in the `DbRoutingState` in `RocksDB`)
3. **SCALED_UP**: Completes the scaling process and activates new partitions for request handling (in the `ClusterConfiguration`)

### Idempotency and Error Handling
- Bootstrap operations are idempotent - repeated bootstrap commands for the same partition are safely ignored
- Scaling commands are distributed commands, they are eventually received by all partitions and are initially sent to partition 1 (i.e. the `Protocol.DEPLOYMENT_PARTITION`)
- Failed scaling operations can be retried without adverse effects

## Implementation notes 

### Snapshot for bootstrap
When a new partition bootstrap, it will fetch from partition 1 the *snapshot for bootstrap*. This is a special kind of snapshot that contains only *ColumnFamilies* that need to be present in the new partition, which corresponds to the column families that are marked with `ColumnFamilyScope.GLOBAL`.

When the first partition asks for a *snapshot for bootstrap*, on partition 1 the following steps happen:

- the *log_idx* of the command SCALE_UP is fetched from `DbRoutingState`. 
- the snapshot store is checked to see if there is a snapshot that has been done after *log_idx*. This is usually unlikely as only a short amount of time is passed from having processed `SCALE_UP` command.
- if no snapshot that satisfy that condition is found, a new *regular snapshot* is taken.
- a *snapshot from bootstrap* is created from the *regular snapshot*

  A new snapshot is created in the folder `bootstrap-snapshots/` as if all the `GLOBAL` columns have been inserted with a single message:
    
    - term=1
    - index=1
    <!-- maybe we should set these as well? -->
    - processedPosition=0 
    - exportedPosition=0
  In the metadata file `zeebe.metadata` it will also be flagged with `isBootstrap=true`
    
- once the snapshot is ready, the first chunk is sent back as a response to the new partition.

  - the new partition will fetch the next chunks until an empty chunk is received, signalling the end of the transfer

- the new partition will then restore using this snapshot before the engine is started

- The snapshot used for bootstrap contains already the changes to `RoutingState` so that the new partitions are already in `RoutingState.desiredPartitions`
- Global state (process definitions, decisions, forms) marked with `ColumnFamilyScope.GLOBAL` is automatically included in new partitions
- Partition-specific state (process instances, jobs, message subscriptions) remains on original partitions until the relocation phase is implemented

## Current Limitations
- **Subscription Migration**: Message and signal subscriptions are not migrated during scaling, but only when relocating.
- **Correlation Imbalance**: Existing message/signal correlations remain on original partitions
- **Gradual Load Distribution**: Full benefits of scaling are realized gradually as new processes are deployed, existing process instances are not migrated.
- **Backups are disabled when scaling**: Backups cannot be taken while scaling is in progress, they can be taken before scaling or after scaling.
