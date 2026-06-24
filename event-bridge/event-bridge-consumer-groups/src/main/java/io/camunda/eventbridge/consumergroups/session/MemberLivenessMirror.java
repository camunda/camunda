/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.session;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe holder of per-group {@link GroupLiveness} snapshots, shared across actors: the
 * leader's heartbeat handler (on the coordinator actor) publishes, and the off-actor session
 * eviction task reads. It carries the ephemeral member liveness that the eviction loop needs but
 * which — unlike topic/group membership — is deliberately not replicated (heartbeats are cheap,
 * leader-local liveness, Kafka-style). Same single-writer/single-reader contract as the
 * replicated-state mirror in {@code DbConsumerGroupState}; constructed at stream startup so the
 * task (built with the record processor) and the coordinator (built on leader activation) share one
 * instance.
 */
public final class MemberLivenessMirror {

  private final Map<String, GroupLiveness> mirror = new ConcurrentHashMap<>();

  public void publish(final String groupId, final GroupLiveness liveness) {
    mirror.put(groupId, liveness);
  }

  public GroupLiveness get(final String groupId) {
    return mirror.get(groupId);
  }

  /** Drops liveness for groups no longer present (their last member left). */
  public void retain(final Set<String> liveGroupIds) {
    mirror.keySet().retainAll(liveGroupIds);
  }

  /**
   * Clears all liveness — used when the node stops leading and the ephemeral state is abandoned.
   */
  public void clear() {
    mirror.clear();
  }
}
