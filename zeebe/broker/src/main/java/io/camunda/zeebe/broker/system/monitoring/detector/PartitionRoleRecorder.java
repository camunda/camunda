/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.system.monitoring.history.HealthEvents;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records this broker's Raft role changes per partition as {@link #BECAME_LEADER} and {@link
 * #BECAME_FOLLOWER} events, and tells detectors which partitions it leads. Every broker records its
 * own view, so the histories of all brokers together show each election: one leader event, and a
 * follower event with the same term on the others.
 */
public final class PartitionRoleRecorder implements PartitionRaftListener, LeaderPartitions {

  public static final String BECAME_LEADER = "BECAME_LEADER";
  public static final String BECAME_FOLLOWER = "BECAME_FOLLOWER";

  private final HealthEvents events;
  private final Set<String> leading = ConcurrentHashMap.newKeySet();

  public PartitionRoleRecorder(final HealthEvents events) {
    this.events = events;
  }

  @Override
  public void onBecameRaftLeader(final PartitionId partitionId, final long term) {
    final var partition = ZeebePartition.componentName(partitionId);
    leading.add(partition);
    events.record(
        partition,
        BECAME_LEADER,
        "became leader in term %d".formatted(term),
        Map.of("term", Long.toString(term)));
  }

  @Override
  public void onBecameRaftFollower(final PartitionId partitionId, final long term) {
    final var partition = ZeebePartition.componentName(partitionId);
    final var steppedDown = leading.remove(partition);
    events.record(
        partition,
        BECAME_FOLLOWER,
        steppedDown
            ? "stepped down as leader and became follower in term %d".formatted(term)
            : "became follower in term %d".formatted(term),
        Map.of("term", Long.toString(term), "steppedDown", Boolean.toString(steppedDown)));
  }

  @Override
  public boolean isLeader(final String child) {
    return leading.contains(child);
  }
}
