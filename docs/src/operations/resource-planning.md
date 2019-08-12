# Resource Planning

The short answer to “_what resources and configuration will I need to take Zeebe to production?_” is: it depends.

While we cannot tell you exactly what you need - beyond _it depends_ - we can explain what depends, what it depends on, and how it depends on it.

## Disk Space

All Brokers in a partition use disk space to store:

- The event log for each partition they participate in. By default, this is a minimum of 512MB for each partition, incrementing in 512MB segments. The event log is truncated on a given broker when data has been processed and successfully exported by all loaded exporters.
- One or more periodic snapshots of the running state (inflight data) of each partition (unbounded, based on in-flight work). The number of snapshots to retain, and their frequency is configurable. By default it is set to three snapshots retained, and a snapshot [every 15 minutes](https://github.com/zeebe-io/zeebe/blob/cca5aeda4bd9d7e7e83f68d221bbf1a3e4d0f000/dist/src/main/config/zeebe.cfg.toml#L145).

Additionally, the leader of a partition also uses disk space to store:
- A projection of the running state of the partition in RocksDB. (unbounded, based on in-flight work)

By default this data is stored in 

- `segments` - the data of the log split into segments. The log is only appended - until it is truncated by reaching the max snapshot count.
- `state` - the active state. Deployed workflows, active workflow instances, etc. Completed workflow instances or jobs are removed.
- `snapshot` - the data of a state at a certain point in time

If you want a recipe to explode your disk space usage, here are a few ways to do it:

- Create a high number of snapshots with a long period between them.
- Load an exporter, such as the Debug Exporter, that does not advance its record position.

## Event Log

The event log for each partition is segmented. By default, the segment size is 512MB. This can be changed in the zeebe.cfg.toml file with the setting [logSegmentSize](https://github.com/zeebe-io/zeebe/blob/0.20.0/dist/src/main/config/zeebe.cfg.toml#L148). 

An event log segment can be deleted once all the events it contains have been processed by exporters, replicated to other brokers, and processed. Three things can cause the event log to not be truncated:

- A cluster loses its quorum, in which case events are queued but not processed. 
- An exporter does not advance its read position in the event log.
- The max number of snapshots has not been written.

An event log segment is not deleted until all the events in it have been exported by all configured exporters. This means that exporters that rely on side-effects, perform intensive computation, or experience back pressure from external storage will cause disk usage to grow, as they delay the deletion of event log segments. 

Exporting is only performed on the partition leader, but the followers of the partition do not delete segments in their replica of the partition until the leader marks all events in it as unneeded by exporters.

No event log segments are deleted until the maximum number of snapshots has been reached. When the maximum number of snapshots has been reached, the event log is truncated up to the oldest snapshot.

## Snapshots

The running state of the partition is captured periodically on the leader in a snapshot. By default, this period is [every 15 minutes](https://github.com/zeebe-io/zeebe/blob/0.20.0/dist/src/main/config/zeebe.cfg.toml#L151). This can be changed in the zeebe.cfg.toml file. The number of valid snapshots to retain is configured in zeebe.cfg.toml. By default, the leader and followers retain only the latest valid snapshot.

A snapshot is a projection of all events that represent the current running state of the workflows running on the partition.  It contains all active data, for example, deployed workflows, active workflow instances, and not yet completed jobs.

When the broker has as many snapshots as configured by the parameter maxSnapshots, it deletes all data on the log which was written before the oldest snapshot.

## RocksDB

On the lead broker of a partition, the current running state is kept in memory, and on disk in RocksDB. In our experiments this grows to 2GB under a heavy load of long-running processes. The snapshots that are replicated to followers are snapshots of RocksDB.

## Effect of exporters and external system failure

If an external system relied on by an exporter fails - for example, if you are exporting data to ElasticSearch and the connection to the ElasticSearch cluster fails - then the exporter will not advance its position in the event log, and brokers cannot truncate their logs. The broker event log will grow until the exporter is able to re-establish the connection and export the data. 
To ensure that your brokers are resilient in the event of external system failure, give them sufficient disk space to continue operating without truncating the event log until the connection to the external system is restored.

## Effect on exporters of node failure

Only the leader of a partition exports events. Only committed events (events that have been replicated) are passed to exporters. The exporter will then update its read position. The exporter read position is only replicated between brokers in the snapshot. It is not itself written to the event log. This means that _an exporter’s current position cannot be reconstructed from the replicated event log, only from a snapshot_. 

When a partition fails over to a new leader, the new leader is able to construct the current partition state by projecting the event log from the point of the last snapshot. The position of exporters cannot be reconstructed from the event log, so it is set to the last snapshot. This means that an exporter can see the same events twice in the event of a fail-over.

You should assign idempotent ids to events in your exporter if this is an issue for your system. The combination of record position and partition id is reliable as a unique id for an event.

## Effect of quorum loss

If a partition goes under quorum (for example: if two nodes in a three node cluster go down), then the leader of the partition will continue to accept requests, but these requests will not be replicated and will not be marked as committed. In this case, they cannot be truncated. This causes the event log to grow. The amount of disk space needed to continue operating in this scenario is a function of the broker throughput and the amount of time to quorum being restored. You should ensure that your nodes have sufficient disk space to handle this failure mode.
