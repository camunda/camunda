/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import java.util.concurrent.CompletableFuture;

/** The leadership that the Raft election decides. It passes every call to the Raft partition. */
public final class RaftPartitionLeadership implements PartitionLeadership {

  private final RaftPartition raftPartition;

  public RaftPartitionLeadership(final RaftPartition raftPartition) {
    this.raftPartition = raftPartition;
  }

  @Override
  public void addListener(final PartitionLeadershipListener listener) {
    raftPartition.addRoleChangeListener(new RaftListener(listener));
  }

  @Override
  public void removeListener(final PartitionLeadershipListener listener) {
    // the wrapper is a record, so a new one equals the one that was added
    raftPartition.removeRoleChangeListener(new RaftListener(listener));
  }

  @Override
  public Role role() {
    return raftPartition.getRole();
  }

  @Override
  public long term() {
    return raftPartition.term();
  }

  @Override
  public CompletableFuture<Void> stepDown() {
    return raftPartition.stepDown();
  }

  private record RaftListener(PartitionLeadershipListener delegate)
      implements RaftRoleChangeListener {
    @Override
    public void onNewRole(final Role newRole, final long newTerm) {
      delegate.onNewRole(newRole, newTerm);
    }
  }
}
