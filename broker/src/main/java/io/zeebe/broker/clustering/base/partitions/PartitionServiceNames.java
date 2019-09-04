/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.partitions;

import io.zeebe.servicecontainer.ServiceName;

public class PartitionServiceNames {

  public static ServiceName<Void> leaderOpenLogStreamServiceName(String raftName) {
    return ServiceName.newServiceName(
        String.format("raft.leader.%s.openLogStream", raftName), Void.class);
  }

  public static ServiceName<Partition> leaderPartitionServiceName(final String partitionName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.%s.leader", partitionName), Partition.class);
  }

  public static final ServiceName<PartitionLeaderElection> partitionLeaderElectionServiceName(
      String logName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.%s.leader.election", logName),
        PartitionLeaderElection.class);
  }

  public static final ServiceName<Void> partitionLeadershipEventListenerServiceName(
      String logName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.%s.role.listener", logName), Void.class);
  }

  public static ServiceName<Partition> followerPartitionServiceName(final String partitionName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.%s.follower", partitionName), Partition.class);
  }

  public static ServiceName<Void> partitionInstallServiceName(final String partitionName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.install.%s", partitionName), Void.class);
  }

  public static ServiceName<Void> leaderInstallServiceRootName(final String partitionName) {
    return ServiceName.newServiceName(
        String.format("cluster.base.partition.leader.install.root.%s", partitionName), Void.class);
  }
}
