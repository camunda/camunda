# Clustering

Zeebe can operate as a cluster of brokers, forming a peer-to-peer network.
In this network, all nodes are equal and there is no single point of failure.

![cluster](/basics/cluster.png)

## Gossip Membership Protocol

Zeebe implements the Gossip protocol to know which nodes are currently part of the cluster.

The cluster is bootstrapped using a set of well-known "bootstrap" or "seed" nodes, to which the other ones can connect. To achieve this, each node should have the bootstrap node(s) as initial contact point in their configuration:

```toml
[network.gossip]
initialContactPoints = [ "node1.mycluster.loc:51016" ]
```

When a node is connected to the cluster for the first time, it fetches the topology from the initial contact point node(s) and then starts gossiping with the other nodes. Nodes keep cluster topology locally across restarts.

## Raft Consensus and Replication Protocol

To ensure fault tolerance, Zeebe replicates data across machines using the Raft protocol.

Data is organized in topics which are divided into partitions (shards). Each partition has a number of replicas. Among the replica set, a leader is determined by the raft protocol which initially takes in requests and performs all the processing. Each node in the cluster may be both leader and follower at the same time, for different partitions.

![cluster](/basics/data-distribution.png)

## Commit

Before an event is processed, it is first replicated to a set of followers which each maintain a copy of the data and must be "committed" as defined by the raft protocol.

![cluster](/basics/commit.png)
