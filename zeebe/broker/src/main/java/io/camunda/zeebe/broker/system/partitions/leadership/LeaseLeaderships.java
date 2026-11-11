/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

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
 */
public final class LeaseLeaderships implements LeadershipListener {

  private final Map<Integer, LeasePartitionLeadership> partitions = new ConcurrentHashMap<>();
  private final LeaderElector elector;

  /**
   * @param createElector creates the elector of the node, with the listener it must tell about the
   *     leadership
   */
  public LeaseLeaderships(final Function<LeadershipListener, LeaderElector> createElector) {
    elector = createElector.apply(this);
  }

  public void start() {
    elector.start();
  }

  public void close() {
    elector.close();
  }

  /** The leadership of a partition. Each call for the same partition returns the same view. */
  public PartitionLeadership forPartition(final int partitionId) {
    return view(partitionId);
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
