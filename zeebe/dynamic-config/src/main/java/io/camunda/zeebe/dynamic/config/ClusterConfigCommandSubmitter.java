/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;

/**
 * Minimal facade for the system partition that the dynamic-config layer uses to route
 * cluster-configuration mutations through the stream processor.
 *
 * <p>This interface is defined here (in the dynamic-config module) rather than in
 * zeebe-system-partition to avoid a circular dependency: system-partition already depends on
 * dynamic-config. {@code SystemPartitionFacadeImpl} implements this interface.
 *
 * <p>When the system partition is not enabled, callers hold a {@code null} reference and fall back
 * to the legacy local-mutation / gossip path.
 */
public interface ClusterConfigCommandSubmitter {

  /**
   * Returns the latest committed {@link ClusterConfiguration} as observed by this replica's commit
   * listener. After the first commit this is guaranteed non-null and at least as fresh as the most
   * recently applied commit on this replica.
   */
  ClusterConfiguration query();

  /**
   * Submit a cluster-configuration command to the system partition.
   *
   * <p>The future completes with the resulting committed event record (e.g. {@code
   * CHANGE_PLAN_STAMPED} for a {@code STAMP_CHANGE_PLAN} command). On a follower, the future fails
   * with a {@code NotLeaderException}; callers must route to the leader.
   */
  ActorFuture<ClusterConfigurationRecord> submitCommand(
      ClusterConfigurationIntent intent, ClusterConfigurationRecord record);
}
