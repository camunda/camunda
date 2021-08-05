package io.camunda.zeebe.broker.partitioning;

import java.util.List;

public interface ClusterPartitionManager {

  List<ClusterPartition> getPartitions();

  ClusterPartition getPartition(int partitionId);

  // ??? Topology getTopology()
}
