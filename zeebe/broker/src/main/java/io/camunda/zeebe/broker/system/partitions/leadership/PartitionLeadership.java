/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.atomix.raft.RaftServer.Role;
import java.util.concurrent.CompletableFuture;

/**
 * Who leads one partition on this node, and in which term. The partition installs its services for
 * the role it is told, and does not care how the role was decided.
 *
 * <p>With Raft, the role comes from the Raft election, which also replicates the log. With the
 * database, it would come from the leases in the database. The roles are those of Raft, so that the
 * partition transitions stay as they are; an implementation that has no followers uses only {@link
 * Role#LEADER} and {@link Role#INACTIVE}.
 */
public interface PartitionLeadership {

  /**
   * Tells the listener about each new role, from the thread of the implementation. The listener
   * also learns the current role right after it is added, as with Raft, so a partition that starts
   * late does not miss that it already leads.
   */
  void addListener(PartitionLeadershipListener listener);

  void removeListener(PartitionLeadershipListener listener);

  /** The current role of this node for the partition. */
  Role role();

  /** The current term. Each new leader has a higher term than the one before it. */
  long term();

  /** Gives up the leadership, if this node leads, so that another node can lead. */
  CompletableFuture<Void> stepDown();
}
