# Resource Planning

The short answer to “_what resources and configuration will I need to take Zeebe to production?_” is: it depends.

While we cannot tell you exactly what you need - beyond _it depends_ - we can explain what depends, what it depends on, and how it depends on it.

## Disk Space

All Brokers in a partition use disk space to store:

- The event log for each partition they participate in. By default, this is a minimum of _512MB_ for each partition, incrementing in 512MB segments. The event log is truncated on a given broker when data has been processed and successfully exported by all loaded exporters.
- One periodic snapshots of the running state (in-flight data) of each partition (unbounded, based on in-flight work). 

Additionally, the leader of a partition also uses disk space to store:
- A projection of the running state of the partition in RocksDB. (unbounded, based on in-flight work)

To calculate the required amount of disk space, the following "back of the envelope" formula can be used as a starting point: 

```
neededDiskSpace = replicatedState + localState

replicatedState = totalEventLogSize + totalSnapshotSize

totalEventLogSize = followerPartitionsPerNode * eventLogSize * reserveForPartialSystemFailure 

totalSnapshotSize = partitionsPerNode * singleSnapshotSize * 2 
// singleSnapshotSize * 2: 
//   the last snapshot (already replicated) +
//   the next snapshot (in transit, while it is being replicated) 

partitionsPerNode = leaderPartitionsPerNde + followerPartitionsPerNode

leaderPartitionsPerNode = partitionsCount / numberOfNodes 
followerPartitionsPerNode = partitionsCount * replicationFactor / numberOfNodes 

clusterSize = [number of broker nodes]
partitionsCount = [number of partitions]
replicationFactor = [number of replicas per partition]
reserveForPartialSystemFailure = [factor to account for partial system failure]  
singleSnapshotSize = [size of a single rocks DB snapshot]                  
eventLogSize = [event log size for duration of snapshotPeriod] 
```

Some observations on the scaling of the factors above:
- `eventLogSize`: This factor scales with the throughput of your system 
- `totalSnapshotSize`: This factor scales with the number of in-flight workflows
- `reserveForPartialSystemFailure`: This factor is supposed to be a reserve to account for partial system failure (e.g. loss of quorum inside Zeebe cluster, or loss of connection to external system). See the remainder of this document for a further discussion on the effects of partial system failure on Zeebe cluster and disk space provisioning.

Many of the factors influencing above formula can be fine-tuned in the [configuration](/appendix/broker-config-template.md). The relevant configuration settings are:
```
Config file
    zeebe:
      broker:
        data:
          logSegmentSize: 512MB
          snapshotPeriod: 15m
        cluster:
          partitionsCount: 1
          replicationFactor: 1
          clusterSize: 1

Environment Variables
  ZEEBE_BROKER_DATA_LOGSEGMENTSIZE = 512MB
  ZEEBE_BROKER_DATA_SNAPSHOTPERIOD = 15m
  ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT = 1
  ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR = 1
  ZEEBE_BROKER_CLUSTER_CLUSTERSIZE = 1
```

Other factors can be observed in a production-like system with representative throughput.

If you want to know where to look, by default this data is stored in 

- `segments` - the data of the log split into segments. The log is only appended - its data can be deleted when it becomes part of a new snapshot.
- `state` - the active state. Deployed workflows, active workflow instances, etc. Completed workflow instances or jobs are removed.
- `snapshot` - a state at a certain point in time

> **Pitfalls** 
> 
> If you want to avoid exploding your disk space usage, here are a few pitfalls to avoid:
> - Do not create a high number of snapshots with a long period between them.
> - Do not configure an exporter which does not not advance its record position (such as the Debug Exporter)

If you do configure an exporter, make sure to monitor its availability and health, as well as the availability and health the exporter depends on. 
This is the Achilles' heel of the cluster. If data cannot be exported, it cannot be removed from the cluster and will accumulate on disk. See _Effect of exporters and external system failure_ further on in this document for an explanation and possible buffering strategies. 

### Event Log

The event log for each partition is segmented. By default, the segment size is 512MB. 

The event log will grow over time, unless and until individual event log segments are deleted.

An event log segment can be deleted once:
 - all the events it contains have been processed by exporters, 
 - all the events it contains have been replicated to other brokers, 
 - all the events it contains have been processed, and
 - the maximum number of snapshots has been reached. 

The following conditions inhibit the automatic deletion of event log segments:
- A cluster loses its quorum. In this case events are queued but not processed. Once a quorum is reestablished, events will be replicated and eventually event log segments will be deleted.
- The max number of snapshots has not been written. Log segment deletion will begin as soon as the max number of snapshots has been reached 
- An exporter does not advance its read position in the event log. In this case the event log will grow ad infinitum.

An event log segment is not deleted until all the events in it have been exported by all configured exporters. This means that exporters that rely on side-effects, perform intensive computation, or experience back pressure from external storage will cause disk usage to grow, as they delay the deletion of event log segments.

Exporting is only performed on the partition leader, but the followers of the partition do not delete segments in their replica of the partition until the leader marks all events in it as unneeded by exporters.

We make sure that event log segments are not deleted too early. No event log segment is deleted until a snapshot has been taken that includes that segment. When a snapshot has been taken, the event log is only deleted up to that point.

### Snapshots

The running state of the partition is captured periodically on the leader in a snapshot. By default, this period is every 15 minutes. This can be changed in the [configuration](/appendix/broker-config-template.md).

A snapshot is a projection of all events that represent the current running state of the workflows running on the partition.  It contains all active data, for example, deployed workflows, active workflow instances, and not yet completed jobs.

When the broker has written a new snapshot, it deletes all data on the log which was written before the latest snapshot.

### RocksDB

On the lead broker of a partition, the current running state is kept in memory, and on disk in RocksDB. In our experience this grows to 2GB under a heavy load of long-running processes. The snapshots that are replicated to followers are snapshots of RocksDB.

### Effect of exporters and external system failure

If an external system relied on by an exporter fails - for example, if you are exporting data to ElasticSearch and the connection to the ElasticSearch cluster fails - then the exporter will not advance its position in the event log, and brokers cannot truncate their logs. The broker event log will grow until the exporter is able to re-establish the connection and export the data.
To ensure that your brokers are resilient in the event of external system failure, give them sufficient disk space to continue operating without truncating the event log until the connection to the external system is restored.

### Effect on exporters of node failure

Only the leader of a partition exports events. Only committed events (events that have been replicated) are passed to exporters. The exporter will then update its read position. The exporter read position is only replicated between brokers in the snapshot. It is not itself written to the event log. This means that _an exporter’s current position cannot be reconstructed from the replicated event log, only from a snapshot_.

When a partition fails over to a new leader, the new leader is able to construct the current partition state by projecting the event log from the point of the last snapshot. The position of exporters cannot be reconstructed from the event log, so it is set to the last snapshot. This means that an exporter can see the same events twice in the event of a fail-over.

You should assign idempotent ids to events in your exporter if this is an issue for your system. The combination of record position and partition id is reliable as a unique id for an event.

### Effect of quorum loss

If a partition goes under quorum (for example: if two nodes in a three node cluster go down), then the leader of the partition will continue to accept requests, but these requests will not be replicated and will not be marked as committed. In this case, they cannot be truncated. This causes the event log to grow. The amount of disk space needed to continue operating in this scenario is a function of the broker throughput and the amount of time to quorum being restored. You should ensure that your nodes have sufficient disk space to handle this failure mode.
