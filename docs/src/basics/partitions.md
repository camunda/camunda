# Partitions

> Note: If you have worked with the [Apache Kafka System](https://kafka.apache.org/) before, the concepts presented on this page will sound very familiar to you.

In Zeebe, all data is organized into *partitions*. A *partition* is a persistent stream of workflow-related events. In a cluster of brokers, partitions are distributed among the nodes so it can be thought of as a *shard*. When you bootstrap a Zeebe broker you can configure how many partitions you need.

## Usage Examples

Whenever you deploy a workflow, you deploy it to the first partition. The workflow is then distributed to all partitions. On all partitions, this workflow receives the same key and version such that it can be consistently identified.

When you start an instance of a workflow, the client library will then route the request to one partition in which the workflow instance will be published. All subsequent processing of the workflow instance will happen in that partition.

## Scalability

Use partitions to scale your workflow processing. Partitions are dynamically distributed in a Zeebe cluster and for each partition there is one leading broker at a time. This *leader* accepts requests and performs event processing for the partition. Let us assume you want to distribute workflow processing load over five machines. You can achieve that by bootstraping five partitions.

## Partition Data Layout

A partition is a persistent append-only event stream. Initially, a partition is empty. As the first entry gets inserted, it takes the place of the first entry. As the second entry comes in and is inserted, it takes the place as the second entry and so on and so forth. Each entry has a position in the partition which uniquely identifies it.

![partition](/basics/partition.png)

## Replication

For fault tolerance, data in a partition is replicated from the leader of the partition to its *followers*. Followers are other Zeebe Broker nodes that maintain a copy of the partition without performing event processing.

## Recommendations

Choosing the number of partitions depends on the use case, workload and cluster setup. Here are some rules of thumb:

* For testing and early development, start with a single partition. Note that Zeebe's workflow processing is highly optimized for efficiency, so a single partition can already handle high event loads.
* With a single Zeebe Broker, a single partition is mostly enough. However, if the node has many cores and the broker is configured to use them, then more partitions can increase the total throughput (~ 2 threads per partition).
* Base your decisions on data. Simulate the expected workload, measure and compare the performance of different partition setups.
