# Partition Distributor

import PartitionDistributionVisualizer from '@site/src/components/PartitionDistributionVisualizer';

Use the visualizer below to explore how Zeebe distributes partitions across brokers for a given
cluster configuration.

<PartitionDistributionVisualizer />

## How it works

Zeebe uses a **round-robin** strategy
([`RoundRobinPartitionDistributor`](https://github.com/camunda/camunda/blob/main/zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/util/RoundRobinPartitionDistributor.java))
to assign partitions to brokers. For a cluster of _N_ brokers, partition _i_ (zero-indexed) is
assigned as:

- **Primary (leader)**: broker `i % N`
- **Followers**: brokers `(i+1) % N` through `(i+RF-1) % N`

The replication factor is capped to the number of brokers.

## Election Priority

Each replica has an election priority. When the current leader fails, Raft promotes the follower
with the highest priority. Zeebe alternates secondary priority assignments every _N_ partitions so
failover leadership spreads evenly across brokers.
