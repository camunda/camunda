/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The desired configuration of a topic as held in the coordinator's replicated registry (keyed by
 * topic name). Encoded to a compact string for {@link DbTopicState}.
 *
 * <p>Placement is decided centrally by the coordinator and carried here as {@code assignment}
 * (partition id &rarr; replica broker node ids): brokers do not derive placement, they obey this
 * map. Storing it as data — rather than re-deriving it deterministically — is what lets the
 * coordinator rebalance later by rewriting the assignment.
 *
 * @param partitionCount number of partitions in the topic's Raft group
 * @param replicationFactor number of replicas per partition
 * @param status lifecycle status of the topic
 * @param assignment committed placement: partition id (1-based) &rarr; ordered replica node ids —
 *     what exists now and what brokers reconcile/provision
 * @param target in-flight reassignment goal (empty when none); the change-coordinator drives {@code
 *     assignment} toward this one safe Raft step at a time. Coordinator-internal — brokers ignore
 *     it and act only on {@code assignment}.
 */
public record TopicMetadata(
    int partitionCount,
    int replicationFactor,
    TopicStatus status,
    Map<Integer, List<Integer>> assignment,
    Map<Integer, List<Integer>> target) {

  /** Convenience for callers/tests that don't carry an assignment (defaults to empty). */
  public TopicMetadata(
      final int partitionCount, final int replicationFactor, final TopicStatus status) {
    this(partitionCount, replicationFactor, status, Map.of(), Map.of());
  }

  /** Convenience for callers that carry a committed assignment but no in-flight target. */
  public TopicMetadata(
      final int partitionCount,
      final int replicationFactor,
      final TopicStatus status,
      final Map<Integer, List<Integer>> assignment) {
    this(partitionCount, replicationFactor, status, assignment, Map.of());
  }

  /** Whether a reassignment is in flight. */
  public boolean hasTarget() {
    return target != null && !target.isEmpty();
  }

  /** Lifecycle of a topic as it is provisioned, served, and torn down. */
  public enum TopicStatus {
    /** Registered; its Raft group is being provisioned across members. */
    CREATING,
    /** Provisioned and serving publish/poll. */
    ACTIVE,
    /** Marked for removal; its Raft group is being torn down. */
    DELETING
  }

  String encode() {
    return partitionCount
        + ";"
        + replicationFactor
        + ";"
        + status.name()
        + ";"
        + encodeAssignment(assignment)
        + ";"
        + encodeAssignment(target);
  }

  /**
   * The committed assignment encoded as {@code pid=n1,n2|...} (for payloads outside this package).
   */
  public String encodedAssignment() {
    return encodeAssignment(assignment);
  }

  static TopicMetadata decode(final String encoded) {
    final var parts = encoded.split(";", 5);
    return new TopicMetadata(
        Integer.parseInt(parts[0]),
        Integer.parseInt(parts[1]),
        TopicStatus.valueOf(parts[2]),
        parts.length > 3 ? decodeAssignment(parts[3]) : Map.of(),
        parts.length > 4 ? decodeAssignment(parts[4]) : Map.of());
  }

  /**
   * Encodes the assignment as {@code pid=n1,n2|pid=n1,n2|...} (empty string when no assignment).
   */
  static String encodeAssignment(final Map<Integer, List<Integer>> assignment) {
    return assignment.entrySet().stream()
        .map(
            e ->
                e.getKey()
                    + "="
                    + e.getValue().stream().map(String::valueOf).collect(Collectors.joining(",")))
        .collect(Collectors.joining("|"));
  }

  static Map<Integer, List<Integer>> decodeAssignment(final String encoded) {
    final Map<Integer, List<Integer>> assignment = new LinkedHashMap<>();
    if (encoded == null || encoded.isBlank()) {
      return assignment;
    }
    for (final var entry : encoded.split("\\|")) {
      final var eq = entry.indexOf('=');
      final var partitionId = Integer.parseInt(entry.substring(0, eq));
      final List<Integer> replicas = new ArrayList<>();
      final var ids = entry.substring(eq + 1);
      if (!ids.isBlank()) {
        for (final var id : ids.split(",")) {
          replicas.add(Integer.parseInt(id));
        }
      }
      assignment.put(partitionId, replicas);
    }
    return assignment;
  }
}
