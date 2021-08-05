package io.camunda.zeebe.broker.partitioning;

import java.util.List;

public interface LocalPartitionManager {

  List<LocalPartition> getLocalPartitions();

  LocalPartition getLocalPartition(int partitionId);
}
