/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotApiRequestHandler;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.health.HealthMonitor;
import java.util.List;

/**
 * Interface encapsulating all the information about a partition that are needed at runtime (i.e.
 * after the transition to the current role has completed)
 */
public interface PartitionContext {

  int getPartitionId();

  PartitionId getFullPartitionId();

  RaftPartition getRaftPartition();

  void notifyListenersOfBecameRaftLeader(final long newTerm);

  void notifyListenersOfBecameRaftFollower(final long newTerm);

  @Deprecated // will be moved to transition logic and happen automatically
  List<ActorFuture<Void>> notifyListenersOfBecomingLeader(final long newTerm);

  @Deprecated // will be moved to transition logic and happen automatically
  List<ActorFuture<Void>> notifyListenersOfBecomingFollower(final long newTerm);

  @Deprecated // will be moved to transition logic and happen automatically
  void notifyListenersOfBecomingInactive();

  Role getCurrentRole();

  long getCurrentTerm();

  HealthMonitor getComponentHealthMonitor();

  StreamProcessor getStreamProcessor();

  ExporterDirector getExporterDirector();

  boolean shouldProcess();

  @Deprecated // currently the implementation forwards this to other components inside the
  // partition; these components will be directly registered as listeners in the future
  void setDiskSpaceAvailable(boolean b);

  TopologyManager getTopologyManager();

  AdminApiRequestHandler getAdminApiService();

  void setAdminApiRequestHandler(AdminApiRequestHandler handler);

  PartitionAdminAccess getAdminAccess();

  void setAdminAccess(PartitionAdminAccess adminAccess);

  DynamicPartitionConfig getDynamicPartitionConfig();

  void setDynamicPartitionConfig(DynamicPartitionConfig partitionConfig);

  SnapshotApiRequestHandler getSnapshotApiRequestHandler();

  ControllableStreamClock getStreamClock();

  void setStreamClock(ControllableStreamClock clock);

  /**
   * Returns the number of partitions in the cluster at the time of partition creation. This number
   * does not update during scale up.
   */
  int getPartitionCount();

  default String getBrokerVersion() {
    return VersionUtil.getVersion();
  }
}
