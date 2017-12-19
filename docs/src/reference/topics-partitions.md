# Topics & Partitions

Every event in Zeebe belongs to a *partition*. Groups of partitions are organized in *topics* which share a common set of workflow definitions. It is up to you to define the granularity of topics and partitions. This section provides assistance with doing that.

## Recommendations

Choosing the number of topics and partitions depends on the use case, workload and cluster setup. Here are some rules of thumb:

* For testing and early development, start with a single topic and a single partition. Note that Zeebe's workflow processing is highly optimized for efficiency, so a single partition can already handle high event loads. See the [Performance section](basics/performance.html) for details.
* With a single Zeebe Broker, a single partition per topic is always enough as there is nothing to scale to.
* Avoid micro topics, i.e. many topics with little throughput. Each partition requires some processing resources and coordination of Zeebe cluster nodes.
* Base your decisions on data. Simulate the expected workload, measure and compare the performance of different topic-partition setups.