/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.mailbox.leadership.LeaderElector;
import io.camunda.zeebe.mailbox.leadership.LeadershipListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The leadership of every partition of a node, from one {@link LeaderElector}. The elector renews
 * all leases of the node in one call, while a partition wants a leadership of its own, so this
 * hands out one {@link LeasePartitionLeadership} per partition and passes the elector's events to
 * it.
 *
 * <p>The elector starts with the first partition that asks for its leadership, so it does not claim
 * partitions before the broker starts them.
 */
public final class LeaseLeaderships implements LeadershipListener, PartitionLeaderships {

  private final Map<Integer, LeasePartitionLeadership> partitions = new ConcurrentHashMap<>();
  private final LeaderElector elector;
  private final Runnable onClose;
  private boolean started;
  private boolean closed;

  /**
   * @param createElector creates the elector of the node, with the listener it must tell about the
   *     leadership
   */
  public LeaseLeaderships(final Function<LeadershipListener, LeaderElector> createElector) {
    this(createElector, () -> {});
  }

  /**
   * @param onClose runs after the elector is closed, for example to stop its thread
   */
  public LeaseLeaderships(
      final Function<LeadershipListener, LeaderElector> createElector, final Runnable onClose) {
    elector = createElector.apply(this);
    this.onClose = onClose;
  }

  public synchronized void start() {
    if (!started && !closed) {
      started = true;
      elector.start();
    }
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    elector.close();
    onClose.run();
  }

  @Override
  public PartitionLeadership forPartition(final RaftPartition raftPartition) {
    final var leadership = forPartition(raftPartition.id().number());
    start();
    return leadership;
  }

  /** The leadership of a partition. Each call for the same partition returns the same view. */
  public PartitionLeadership forPartition(final int partitionId) {
    return view(partitionId);
  }

  @Override
  public boolean isLeaseMode() {
    return true;
  }

  @Override
  public void onLeading(final int partitionId, final long term) {
    view(partitionId).becameLeader(term);
  }

  @Override
  public void onNotLeading(final int partitionId, final long term, final Reason reason) {
    view(partitionId).stoppedLeading(term);
  }

  private LeasePartitionLeadership view(final int partitionId) {
    return partitions.computeIfAbsent(
        partitionId, id -> new LeasePartitionLeadership(id, elector::stepDown));
  }
}
