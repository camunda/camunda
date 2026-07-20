/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.assignor;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The assignor's proposed target: the active assignment (unchanged shape/behavior from before
 * standby support), plus an optional standby assignment (consumer-groups ADR 0006 decision 1) — a
 * separate {@code memberId -> partitions} map, since a partition's standby holders are a different
 * set of members than its active owner.
 */
public class PartitionAssignment {

  private final Map<String, List<TopicPartition>> assignments;
  private final Map<String, List<TopicPartition>> standbyAssignments;

  public PartitionAssignment(final Map<String, List<TopicPartition>> raw) {
    this(raw, Map.of());
  }

  public PartitionAssignment(
      final Map<String, List<TopicPartition>> raw,
      final Map<String, List<TopicPartition>> standby) {
    assignments = Collections.unmodifiableMap(copyOf(raw));
    standbyAssignments = Collections.unmodifiableMap(copyOf(standby));
  }

  private static Map<String, List<TopicPartition>> copyOf(
      final Map<String, List<TopicPartition>> raw) {
    final var lists = new HashMap<String, List<TopicPartition>>();
    raw.forEach((consumer, partitions) -> lists.put(consumer, List.copyOf(partitions)));
    return lists;
  }

  public List<TopicPartition> forConsumer(final String memberId) {
    return assignments.getOrDefault(memberId, List.of());
  }

  public Map<String, List<TopicPartition>> assignments() {
    return assignments;
  }

  /** The member's standby target; empty for a member with no standby role. */
  public List<TopicPartition> standbyForConsumer(final String memberId) {
    return standbyAssignments.getOrDefault(memberId, List.of());
  }

  public Map<String, List<TopicPartition>> standbyAssignments() {
    return standbyAssignments;
  }

  /** The assign/revoke delta and the full target for one member during reconciliation. */
  public record ReconciliationResult(
      List<TopicPartition> revoke, List<TopicPartition> assign, List<TopicPartition> assignment) {}
}
