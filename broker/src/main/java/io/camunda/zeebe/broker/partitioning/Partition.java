package io.camunda.zeebe.broker.partitioning;

public interface Partition {

  PartitionWriteAccess writeAccess();

  PartitionQueryAccess queryAccess();
}
