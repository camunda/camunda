# Upgrade Zeebe

This section describes how to upgrade Zeebe to a new version.

> **Important:** Currently, we are facing an [issue](https://github.com/zeebe-io/zeebe/issues/5581) that can corrupt the data when upgrading to a new version. The issue affects the reprocessing (i.e. rehydrating the data from the records on the log stream) and can be omitted by restoring the data from a snapshot. Please follow the recommended procedure to minimize the risk of losing data.

## Rolling Upgrade

Zeebe is designed to allow a rolling upgrade of a cluster. The brokers can be upgrade one after the other. The other brokers in the cluster continue processing until the whole upgrade is done.

1. Upgrade the first broker and wait until it is ready again
1. Continue with the next broker until all brokers are upgraded
1. Upgrade the standalone gateways

> Because of current issue, it is not recommended to perform a rolling upgrade. Please follow the upgrade procedure instead.

## Upgrade Procedure

The following procedure describes how to upgrade a broker. If the cluster contains multiple brokers then these steps can be done for all brokers in parallel. Standalone gateways should be upgraded after all brokers in the cluster are upgraded to avoid mismatches in the protocol version.

Note that this procedure results in a downtime of the whole cluster. Currently, a rolling upgrade is not recommended to avoid that an upgraded broker do reprocessing and is affected by the issue.

### Upgrade in Kubernetes
#### Preparing the Upgrade
##### From broker version < 24.4

1. Stop the workflow processing
    * Close all job workers
    * Interrupt the incoming connections to avoid user commands
1. Wait until a snapshot is created for all partitions
    * By default, a snapshot is created every 15 minutes
    * Verify that a snapshot is created by looking at the [Metric](/operations/metrics.md) `zeebe_snapshot_count` on the leader and the followers
    * Note that no snapshot is created if no processing happened since the last snapshot
1. Make a backup of the pvc's
    * If you're using Kubernetes 1.17+, then there is a new beta feature, which you can use to snapshot your pvc. Check [this documentation](https://kubernetes.io/docs/concepts/storage/volume-snapshots/) for more information.
    * If you're using an older Kubernetes version then you need to create snapshot's of your pvc's with help of your cloud provider.  For example for the google cloud kubernetes engine follow [this guide](https://cloud.google.com/compute/docs/disks/create-snapshots).

##### From broker version >= 24.4

The [Partitions Admin Endpoint](#partitions-admin-endpoint) can be used to query the status of the partitions and perform operations for the upgrade.

First we need stop the workflow processing and trigger a snapshot creation.
In order to do that we need to call the `prepareUpgrade` operation on the partitions endpoint.
You need to port-forward to each Zeebe pod and send a POST request to the endpoint.

**Example:**
Forward traffic to the pod's metric port (default 9600).
```shell script
$ kubectl port-forward PODNAME 9600
Forwarding from 127.0.0.1:9600 -> 9600
Forwarding from [::1]:9600 -> 9600
```

Send post request to the `prepareUpgrade` endpoint.
```shell script
$ curl -X POST localhost:9600/actuator/partitions/prepareUpgrade
{"1":{"role":"FOLLOWER","snapshotId":null,"processedPosition":null,"processedPositionInSnapshot":null,"streamProcessorPhase":null},"2":{"role":"FOLLOWER","snapshotId":null,"processedPosition":null,"processedPositionInSnapshot":null,"streamProcessorPhase":null},"3":{"role":"LEADER","snapshotId":null,"processedPosition":-1,"processedPositionInSnapshot":null,"streamProcessorPhase":"PAUSED"}}
```

**You need to do this for all pods (brokers) to make sure that you paused all partition leaders.**

Query the status of the partitions:
```shell script
$ curl -X GET localhost:9600/actuator/partitions
{"1":{"role":"FOLLOWER","snapshotId":null,"processedPosition":null,"processedPositionInSnapshot":null,"streamProcessorPhase":null},"2":{"role":"FOLLOWER","snapshotId":null,"processedPosition":null,"processedPositionInSnapshot":null,"streamProcessorPhase":null},"3":{"role":"LEADER","snapshotId":null,"processedPosition":-1,"processedPositionInSnapshot":null,"streamProcessorPhase":"PAUSED"}}
```

until the following conditions are met for all partitions:
  * `processedPositionInSnapshot` is equal to `processedPosition`
  * all followers (role: `FOLLOWER`) have the same `snapshotId` as the leader (role: `LEADER`)

After the processing is paused and snapshot have been taken successfully you should take a backup of your pvc's.

  * If you're using Kubernetes 1.17+, then there is a new beta feature, which you can use to snapshot your pvc. Check [this documentation](https://kubernetes.io/docs/concepts/storage/volume-snapshots/) for more information.
  * If you're using an older Kubernetes version then you need to create snapshot's of your pvc's with help of your cloud provider.  For example for the google cloud kubernetes engine follow [this guide](https://cloud.google.com/compute/docs/disks/create-snapshots).

#### Performing the Upgrade

If you using helm you can update the image tag in you values file.

```yaml
image:
  repository: camunda/zeebe
  tag: NEW_VERSION_TAG
```

After you did this you can run `helm upgrade` to roll out the new version.

```shell script
 helm upgrade RELEASE_NAME zeebe/zeebe-cluster -f VALUES_FILE_NAME.yaml
```

#### Verifying the Upgrade

The upgrade is successful if the following conditions are met:

* the broker's are ready (see [Ready Check](/operations/health.md#ready-check))
* the broker's are healthy (see [Health Check](/operations/health.md#health-check))
* all partitions are healthy (see the [Metric](/operations/metrics.md#metrics-related-to-health) `zeebe_health`)
* the stream processors of the partition leaders are in the phase `PROCESSING` (see [Partitions Admin Endpoint](#partitions-admin-endpoint))

If the upgrade failed because of a known issue, then a partition change its status to unhealthy, and the log output may contain the following error message:

<details>
  <summary>Sample Upgrade Error Message</summary>
  <p>

```
Unexpected error on recovery happens.
io.zeebe.engine.processor.InconsistentReprocessingException: Reprocessing issue detected!
  Restore the data from a backup and follow the recommended upgrade procedure. [cause:
  "The key of the record on the log stream doesn't match to the record from reprocessing.",
  log-stream-record: {"partitionId":1,"value":{"version":1,"bpmnProcessId":"parallel-tasks",
  "workflowKey":2251799813685249,"parentElementInstanceKey":-1,"parentWorkflowInstanceKey":-1,
  "bpmnElementType":"PARALLEL_GATEWAY","flowScopeKey":2251799813685251,
  "elementId":"ExclusiveGateway_0tkgnd5","workflowInstanceKey":2251799813685251},
  "key":2251799813685256,"sourceRecordPosition":4294997784,"valueType":"WORKFLOW_INSTANCE",
  "timestamp":1601025180728,"recordType":"EVENT","intent":"ELEMENT_ACTIVATING",
  "rejectionType":"NULL_VAL","rejectionReason":"","position":4294998112},
  reprocessing-record: {key=2251799813685255, sourceRecordPosition=4294997784,
  intent=WorkflowInstanceIntent:ELEMENT_ACTIVATING, recordType=EVENT}]
```

  </p>
</details>

In this case, the broker should be rolled back to the previous version and the backup should be restored. Ensure that the upgrade was prepared correctly. If it is still unclear why it was not successful then please contact the Zeebe team and ask for guidance.

### Upgrade On-Prem

#### Preparing the Upgrade

##### From broker version < 24.4

1. Stop the workflow processing
    * Close all job workers
    * Interrupt the incoming connections to avoid user commands
1. Wait until a snapshot is created for all partitions
    * By default, a snapshot is created every 15 minutes
    * Verify that a snapshot is created by looking at the [Metric](/operations/metrics.md) `zeebe_snapshot_count` on the leader and the followers
    * Note that no snapshot is created if no processing happened since the last snapshot
1. Make a backup of the `data` folder

##### From broker version >= 24.4

The [Partitions Admin Endpoint](#partitions-admin-endpoint) can be used to query the status of the partitions and perform operations for the upgrade.

1. Stop the workflow processing and trigger a snapshot creation
    * Call the `prepareUpgrade` operation on the partitions endpoint
    * Query the status of the partitions until the following conditions are met for all partitions:
        * `processedPositionInSnapshot` is equal to `processedPosition`
        * all followers (role: `FOLLOWER`) have the same `snapshotId` as the leader (role: `LEADER`)
1. Make a backup of the `data` folder

#### Performing the Upgrade

1. Shut down the broker
1. Replace the `/bin` and `/lib` folders with the versions of the new distribution
1. Start up the broker

#### Verifying the Upgrade

The upgrade is successful if the following conditions are met:

* the broker is ready (see [Ready Check](/operations/health.md#ready-check))
* the broker is healthy (see [Health Check](/operations/health.md#health-check))
* all partitions are healthy (see the [Metric](/operations/metrics.md#metrics-related-to-health) `zeebe_health`)
* the stream processors of the partition leaders are in the phase `PROCESSING` (see [Partitions Admin Endpoint](#partitions-admin-endpoint))

If the upgrade failed because of a known issue then a partition change its status to unhealthy, and the log output may contain the following error message:

<details>
  <summary>Sample Upgrade Error Message</summary>
  <p>

```
Unexpected error on recovery happens.
io.zeebe.engine.processor.InconsistentReprocessingException: Reprocessing issue detected!
  Restore the data from a backup and follow the recommended upgrade procedure. [cause:
  "The key of the record on the log stream doesn't match to the record from reprocessing.",
  log-stream-record: {"partitionId":1,"value":{"version":1,"bpmnProcessId":"parallel-tasks",
  "workflowKey":2251799813685249,"parentElementInstanceKey":-1,"parentWorkflowInstanceKey":-1,
  "bpmnElementType":"PARALLEL_GATEWAY","flowScopeKey":2251799813685251,
  "elementId":"ExclusiveGateway_0tkgnd5","workflowInstanceKey":2251799813685251},
  "key":2251799813685256,"sourceRecordPosition":4294997784,"valueType":"WORKFLOW_INSTANCE",
  "timestamp":1601025180728,"recordType":"EVENT","intent":"ELEMENT_ACTIVATING",
  "rejectionType":"NULL_VAL","rejectionReason":"","position":4294998112},
  reprocessing-record: {key=2251799813685255, sourceRecordPosition=4294997784,
  intent=WorkflowInstanceIntent:ELEMENT_ACTIVATING, recordType=EVENT}]
```

  </p>
</details>

In this case, the broker should be rolled back to the previous version and the backup should be restored. Ensure that the upgrade was prepared correctly. If it is still unclear why it was not successful then please contact the Zeebe team and ask for guidance.

## Partitions Admin Endpoint

This endpoint allows querying the status of the partitions and performing operations to prepare an upgrade.

It is available under `http://{zeebe-broker}:{zeebe.broker.network.monitoringApi.port}/actuator/partitions` (default port: `9600`).
The endpoint is enabled by default. It can be disabled in the configuration by setting:

```
management.endpoint.partitions.enabled=false
```

> In Zeebe version `0.23`, the endpoint is available under `http://{zeebe-broker}:{zeebe.broker.network.monitoringApi.port}/partitions` instead and cannot be disabled.

### Query the Partition Status

The status of the partitions can be queried by a `GET` request:
```
/actuator/partitions
```

The response contains all partitions of the broker mapped to the partition-id.

<details>
  <summary>Full Response</summary>
  <p>

```
{
    "1":{
        "role":"LEADER",
        "snapshotId":"399-1-1601275126554-490-490",
        "processedPosition":490,
        "processedPositionInSnapshot":490,
        "streamProcessorPhase":"PROCESSING"
    }
}
```

  </p>
</details>

### Trigger a Partition Operation

An operation can be triggered using a `POST` request with an empty body. It is applied to all partitions of the broker. The following operations are available:
* `prepareUpgrade`: combines `pauseProcessing` and `takeSnapshot`
    ```
    /actuator/partitions/prepareUpgrade
    ```
* `pauseProcessing`: stop the workflow processing
    ```
    /actuator/partitions/pauseProcessing
    ```
* `resumeProcessing`: start the workflow processing
    ```
    /actuator/partitions/resumeProcessing
    ```
* `takeSnapshot`: create a new snapshot
    ```
    /actuator/partitions/takeSnapshot
    ```

The response of the operation is the current status of the partitions. It is sent after the operation is triggered. It doesn't wait until the operation is performed.
