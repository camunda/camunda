/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.session;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe holder of per-group ready-standby snapshots (consumer-groups ADR 0006 decision 1),
 * shared across actors exactly like {@link MemberLivenessMirror}: the leader's heartbeat handler
 * (on the coordinator actor) publishes {@code memberId -> ready standby partitions} after every
 * reconcile, and the off-actor {@code RebalanceAssignorTask} reads it to (a) decide which member to
 * promote when a partition's active owner has left, and (b) find groups worth re-proposing as soon
 * as a standby catches up, without waiting for the next membership-driven rebalance.
 *
 * <p>Deliberately not replicated: readiness is a per-heartbeat, leader-local observation (cheap to
 * lose and rebuild after a coordinator failover, like liveness) — only the eventual promotion
 * decision the assignor commits is durable.
 */
public final class StandbyReadinessMirror {

  private final Map<String, Map<String, Set<TopicPartition>>> mirror = new ConcurrentHashMap<>();

  public void publish(final String groupId, final Map<String, Set<TopicPartition>> readyStandbys) {
    if (readyStandbys == null || readyStandbys.isEmpty()) {
      mirror.remove(groupId);
    } else {
      mirror.put(groupId, readyStandbys);
    }
  }

  /** {@code memberId -> ready standby partitions} for the group, or empty if none reported. */
  public Map<String, Set<TopicPartition>> get(final String groupId) {
    return mirror.getOrDefault(groupId, Map.of());
  }

  /**
   * The groups that currently have at least one reported ready standby — the assignor's extra work
   * set.
   */
  public Set<String> groupIds() {
    return Set.copyOf(mirror.keySet());
  }

  /** Drops readiness for groups no longer present (their last member left). */
  public void retain(final Set<String> liveGroupIds) {
    mirror.keySet().retainAll(liveGroupIds);
  }

  /** Clears all readiness — used when the node stops leading. */
  public void clear() {
    mirror.clear();
  }
}
