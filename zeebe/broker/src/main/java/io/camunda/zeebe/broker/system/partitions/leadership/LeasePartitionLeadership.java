/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.atomix.raft.RaftServer.Role;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.IntFunction;

/**
 * The leadership of one partition, as the leases in the database decide it. One elector runs for
 * all partitions of the node; {@link LeaseLeaderships} passes its events to this view.
 *
 * <p>A node that does not lead is {@link Role#INACTIVE}: it installs no services and does not
 * replay the log. It is only a standby that claims the lease when the leader is gone, and then
 * replays the log as the new leader.
 */
public final class LeasePartitionLeadership implements PartitionLeadership {

  private final int partitionId;
  private final IntFunction<CompletableFuture<Void>> stepDown;
  private final Set<PartitionLeadershipListener> listeners = new CopyOnWriteArraySet<>();
  private Role role = Role.INACTIVE;
  private long term;

  /**
   * @param stepDown gives up the lease of a partition
   */
  LeasePartitionLeadership(
      final int partitionId, final IntFunction<CompletableFuture<Void>> stepDown) {
    this.partitionId = partitionId;
    this.stepDown = stepDown;
  }

  @Override
  public synchronized void addListener(final PartitionLeadershipListener listener) {
    listeners.add(listener);
    listener.onNewRole(role, term);
  }

  /**
   * When the last listener goes while this node leads, the partition stopped, for example after a
   * failure it cannot recover from. Nobody acts on the lease then, so it is given up.
   */
  @Override
  public synchronized void removeListener(final PartitionLeadershipListener listener) {
    if (listeners.remove(listener) && listeners.isEmpty() && role == Role.LEADER) {
      stepDown();
    }
  }

  @Override
  public boolean isStandbyWhenInactive() {
    return true;
  }

  @Override
  public synchronized Role role() {
    return role;
  }

  @Override
  public synchronized long term() {
    return term;
  }

  @Override
  public CompletableFuture<Void> stepDown() {
    return stepDown.apply(partitionId);
  }

  synchronized void becameLeader(final long newTerm) {
    change(Role.LEADER, newTerm);
  }

  /** The node no longer leads, and is a standby again. The term stays until it leads again. */
  synchronized void stoppedLeading(final long oldTerm) {
    change(Role.INACTIVE, Math.max(term, oldTerm));
  }

  private void change(final Role newRole, final long newTerm) {
    role = newRole;
    term = newTerm;
    listeners.forEach(listener -> listener.onNewRole(newRole, newTerm));
  }
}
