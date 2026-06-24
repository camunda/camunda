/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.topic;

import java.util.List;
import java.util.Map;

/**
 * The desired configuration of a topic as held in the metadata group's replicated registry (keyed
 * by topic name) — the immutable domain/mirror value. It is stored as structured msgpack via {@link
 * PersistedTopic}; this record carries no encoding of its own.
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
}
