# Partition Distribution

Zeebe distributes partitions across brokers using a **round-robin** strategy, implemented in
[`RoundRobinPartitionDistributor`](https://github.com/camunda/camunda/blob/main/zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/util/RoundRobinPartitionDistributor.java).

For a cluster of _N_ brokers, partition _i_ (zero-indexed) is assigned as follows:

- **Primary (leader)**: broker `i % N`
- **Followers**: brokers `(i+1) % N` through `(i+RF−1) % N`

The replication factor is automatically capped to the number of brokers.

import PartitionDistributionVisualizer from '@site/src/components/PartitionDistributionVisualizer';

## Interactive Visualizer

<PartitionDistributionVisualizer />

## Election Priority

Each replica is assigned an election priority. When the current leader fails, Raft elects the
follower with the highest priority as the new leader.

Zeebe alternates the secondary priority assignment every _N_ partitions (descending, then ascending,
then descending, …). This ensures that if broker 0 fails, the failover leadership is spread evenly
across brokers 1 and 2 rather than always landing on broker 1.

Toggle **Show priority numbers** in the visualizer above to see priority values for your
configuration.
