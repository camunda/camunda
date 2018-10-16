# Partitions

Every event in Zeebe belongs to a *partition*. All existing partitions share a common set of workflow definitions. It is up to you to define the granularity of partitions. This section provides assistance with doing that.

## Recommendations

Choosing the number of partitions depends on the use case, workload and cluster setup. Here are some rules of thumb:

* For testing and early development, start with a single partition. Note that Zeebe's workflow processing is highly optimized for efficiency, so a single partition can already handle high event loads. See the [Performance section](basics/performance.html) for details.
* With a single Zeebe Broker, a single partition is always enough as there is nothing to scale to.
* Base your decisions on data. Simulate the expected workload, measure and compare the performance of different partition setups.
