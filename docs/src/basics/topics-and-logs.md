# Topics & Logs

In Zeebe, all data is organized into topics.

> Note: If you have worked with the [Apache Kafka System](https://kafka.apache.org/), the concepts presented on this page will sound very familiar to you.

You can think of a topic as a never ending ordered list of entries.

## Partitions

For scalability, topics are divided into partitions. You can think about a partition like a "shard".

## Entries and Positions

Initially, a partition is empty. As the first entry gets inserted, it takes the place of the first entry. As the second entry comes in and is inserted, it takes the place as the second entry and so on and so forth. Each entry has a position in the partition which uniquely identifies it.

![partition](/basics/partition.png)

## Replication

Data in a partition is replicated from the leader of the partition to the followers.
