/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.membership;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The coordinator's <em>ephemeral</em> per-member reconciliation state, held in memory on the
 * leader's {@link ConsumerGroupCoordinator} actor (never replicated). It tracks the member's
 * liveness (last heartbeat) and the assignment it has confirmed owning, so the heartbeat handler
 * can drive the assign/revoke handshake toward the durable target. A new leader rebuilds this from
 * heartbeats after failover; the durable membership + target come from replicated state.
 */
final class MemberSession {

  private final String memberId;
  private Set<Integer> confirmedAssignment;
  private long confirmedEpoch;
  private Instant lastHeartbeat;

  MemberSession(
      final String memberId,
      final List<Integer> confirmedAssignment,
      final long confirmedEpoch,
      final Instant lastHeartbeat) {
    this.memberId = memberId;
    this.confirmedAssignment = new HashSet<>(confirmedAssignment);
    this.confirmedEpoch = confirmedEpoch;
    this.lastHeartbeat = lastHeartbeat;
  }

  String memberId() {
    return memberId;
  }

  Set<Integer> confirmedAssignment() {
    return confirmedAssignment;
  }

  long confirmedEpoch() {
    return confirmedEpoch;
  }

  void confirm(final long epoch, final List<Integer> assignment) {
    confirmedEpoch = epoch;
    confirmedAssignment = new HashSet<>(assignment);
  }

  Instant lastHeartbeat() {
    return lastHeartbeat;
  }

  void touch(final Instant now) {
    lastHeartbeat = now;
  }

  boolean isExpired(final Instant deadline) {
    return lastHeartbeat.isBefore(deadline);
  }
}
