/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.partitions;

import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.servicecontainer.ServiceName;

public class PartitionServiceNames {

  public static ServiceName<Void> leaderOpenLogStreamServiceName(final String raftName) {
    return ServiceName.newServiceName(
        String.format("raft.leader.%s.openLogStream", raftName), Void.class);
  }

  public static ServiceName<Partition> leaderPartitionServiceName(final String partitionName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.%s.leader", partitionName), Partition.class);
  }

  public static ServiceName<SnapshotStorage> snapshotStorageServiceName(final int partitionId) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.%d.snapshots", partitionId), SnapshotStorage.class);
  }

  public static ServiceName<PartitionLeaderElection> partitionLeaderElectionServiceName(
      final String logName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.%s.leader.election", logName),
        PartitionLeaderElection.class);
  }

  public static ServiceName<Partition> followerPartitionServiceName(final String partitionName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.%s.follower", partitionName), Partition.class);
  }

  public static ServiceName<PartitionInstallService> partitionInstallServiceName(
      final String partitionName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.install.%s", partitionName),
        PartitionInstallService.class);
  }

  public static ServiceName<Void> leaderInstallServiceRootName(final String partitionName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.leader.install.root.%s", partitionName), Void.class);
  }
}
