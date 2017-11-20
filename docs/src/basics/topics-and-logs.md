# Topics & Logs

> Note: If you have worked with the [Apache Kafka System](https://kafka.apache.org/) before, the concepts presented on this page will sound very familiar to you.

In Zeebe, all data is organized into *topics*. Topics are user-defined and have a name for distinction. Each topic is physically organized into one or more *partitions*. Partitions are persistent streams of workflow-related events. In a cluster of Zeebe Broker nodes, partitions are distributed among the nodes so a partition can be thought of as a *shard*. When we create a topic, we specify its name and how many partitions we need.

## Usage Examples

Whenever we deploy a workflow, we deploy it to a specific topic. The workflow is then distributed to all partitions of that topic. On all partitions, this workflow receives the same key and version such that it can be consistently identified within the topic.

When we start an instance of a workflow, we identify the topic this request addresses. The client library will then route the request to one partition of the topic in which the workflow instance will be published. All subsequent processing of the workflow instance will happen in that partition.

## Use Cases

### Data Separation

Use topics to separate your workflow-related data. Let us assume we operate Zeebe for different departments of our organization. We can create different topics for each department ensuring that their workflows do not interfere. For example, workflow versioning applies per topic.

### Scalability

Use partitions to scale your workflow processing. Partitions are dynamically distributed among cluster nodes and for each partition there is one leading cluster node at a time. This *leader* accepts requests and performs event processing for the partition. Let us assume we want to distribute a topic's workflow processing load over five machines. We can achieve that by creating a topic with five partitions (note: it is generally possible that two partitions of the same topic are lead by the same cluster node).

### Quality of Service

Use topics to assign dedicated resources to time-critical workflow events. A Zeebe broker reserves event processing resources to each partition. If we have some workflows where processing latency is critical and some with very high event ingestion, we can create separate topics for each such that the time-critical workflows are less interfered with by the mass of unrelated events.

## Recommendations

Choosing the number of topics and partitions depends on the use case, workload and cluster setup. Here are some rules of thumb:

* For testing and early development, start with a single topic and a single partition. Note that Zeebe's workflow processing is highly optimized for efficiency, so a single partition can already handle high event loads. See the [Performance section](basics/performance.html) for details.
* With a single Zeebe Broker, a single partition per topic is always enough as there is nothing to scale to.
* Avoid micro topics, i.e. many topics with little throughput. Each partition requires some processing resources and coordination of Zeebe cluster nodes.
* Base your decisions on data. Simulate the expected workload, measure and compare the performance of different topic-partition setups.


## Partition Data Layout

A partition is a persistent append-only event stream. Initially, a partition is empty. As the first entry gets inserted, it takes the place of the first entry. As the second entry comes in and is inserted, it takes the place as the second entry and so on and so forth. Each entry has a position in the partition which uniquely identifies it.

![partition](/basics/partition.png)

## Replication

For fault tolerance, data in a partition is replicated from the leader of the partition to its *followers*. Followers are other Zeebe Broker nodes that maintain a copy of the partition without performing event processing. See the [Clustering section](basics/clustering.html) for details on the replication algorithm.
