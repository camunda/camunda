/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

/**
 * Experimental: configuration for the system partition that stores the authoritative
 * ClusterConfiguration via Raft.
 *
 * <p>When {@link #isEnabled()} is true, the broker starts a system-partition Raft replica during
 * startup. The leader of that partition becomes the coordinator for cluster-configuration changes
 * (replacing the static "broker 0 is coordinator" rule).
 *
 * <p>Greenfield only — there is no in-place migration for existing clusters today.
 */
public class SystemPartitionCfg {

  /**
   * Sentinel: when {@code replicationFactor} is left at 0 the system partition uses the same
   * replication factor as the data partitions ({@code cluster.replicationFactor}).
   */
  public static final int REPLICATION_FACTOR_MATCH_DATA = 0;

  private boolean enabled = false;
  private int replicationFactor = REPLICATION_FACTOR_MATCH_DATA;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public void setReplicationFactor(final int replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  @Override
  public String toString() {
    return "SystemPartitionCfg{enabled="
        + enabled
        + ", replicationFactor="
        + replicationFactor
        + '}';
  }
}
