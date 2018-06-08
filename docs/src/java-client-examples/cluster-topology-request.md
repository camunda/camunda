# Request Cluster Topology

Shows which broker is leader and follower for which partition. Particularly useful when you run a cluster with multiple Zeebe brokers.

## Prerequisites

1. Running Zeebe broker with endpoint `localhost:51015` (default)

## TopologyViewer.java

```java
{{#include ../../../samples/src/main/java/io/zeebe/example/cluster/TopologyViewer.java}}
```
