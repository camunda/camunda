package io.zeebe.broker;

import io.zeebe.broker.clustering.base.partitions.Partition;

public interface PartitionListener {

  void onBecomingFollower(Partition partition);
  void onBecomingLeader(Partition partition);
}
