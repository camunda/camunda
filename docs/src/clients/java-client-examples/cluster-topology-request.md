# Request Cluster Topology

Shows which broker is leader and follower for which partition. Particularly useful when you run a cluster with multiple Zeebe brokers.

## Related Resources

* [Clustering Basics](/basics/clustering.html)
* [Setting up a Cluster](/operations/setting-up-a-cluster.html)

## Prerequisites

1. Running Zeebe broker with endpoint `localhost:26500` (default)

## TopologyViewer.java

[Source on github](https://github.com/zeebe-io/zeebe/tree/{{commit}}/samples/src/main/java/io/zeebe/example/cluster/TopologyViewer.java)

```java
{{#include ../../../../samples/src/main/java/io/zeebe/example/cluster/TopologyViewer.java}}
```
