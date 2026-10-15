/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import java.util.Arrays;
import java.util.Optional;

/**
 * The keys of the system partition's key-value store. Each key is identified in the log and in
 * snapshots by its {@link #id()}, not its name or ordinal, so constants can be renamed or reordered
 * safely; an id must never be reused for a different key.
 */
public enum SystemPartitionKeyType {
  /**
   * The id identifying the cluster, set when the cluster is first bootstrapped and replaced only
   * when a different one is configured.
   */
  CLUSTER_ID(1),
  /** When the cluster was first bootstrapped, set once alongside {@link #CLUSTER_ID}. */
  CLUSTER_CREATION_TIME(2),
  /** The latest tick of the failure detector, put by the leader every tick interval. */
  TICK(3),
  /** The latest tick each broker is known to have seen, put by the failure detector's leader. */
  CLUSTER_VIEW(4),
  /**
   * The decision to fail over from a failed region to a surviving one, put once by the failure
   * detector's leader together with the tick that made the decision.
   */
  REGION_FAILOVER(5),
  /**
   * The cluster configuration as last written by a configuration change coordinator, compared and
   * set by its next change id.
   */
  CLUSTER_CONFIGURATION(6);

  private final long id;

  SystemPartitionKeyType(final long id) {
    this.id = id;
  }

  public long id() {
    return id;
  }

  /** Returns the key with the given id, or empty if this broker does not know it. */
  public static Optional<SystemPartitionKeyType> fromId(final long id) {
    return Arrays.stream(values()).filter(key -> key.id == id).findFirst();
  }
}
