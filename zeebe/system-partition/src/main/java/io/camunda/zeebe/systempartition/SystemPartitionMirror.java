/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges dynamic-config state into the system partition.
 *
 * <p>Subscribed as a cluster-configuration update listener, it forwards every locally observed
 * change to {@link SystemPartition#update(ClusterConfiguration)} when this broker is the system
 * partition leader. Failures are logged but not surfaced — this is a "shadow" path while the system
 * partition is opt-in (hackday scope). When the read path is flipped to read from the system
 * partition (follow-up), this class becomes the canonical write hop.
 */
public final class SystemPartitionMirror implements ClusterConfigurationUpdateListener {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPartitionMirror.class);

  private final SystemPartition systemPartition;

  public SystemPartitionMirror(final SystemPartition systemPartition) {
    this.systemPartition = systemPartition;
  }

  @Override
  public void onClusterConfigurationUpdated(final ClusterConfiguration configuration) {
    if (configuration == null || configuration.isUninitialized()) {
      return;
    }
    if (!systemPartition.isLeader()) {
      // Non-leaders observe updates via the leader's Raft replication, not via this mirror.
      return;
    }
    systemPartition
        .update(configuration)
        .onComplete(
            (committed, err) -> {
              if (err != null) {
                LOG.warn(
                    "Failed to mirror cluster configuration version {} to system partition: {}",
                    configuration.version(),
                    err.toString());
              } else {
                LOG.debug(
                    "Mirrored cluster configuration version {} to system partition (committed)",
                    committed.version());
              }
            });
  }
}
