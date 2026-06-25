/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.membership;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.FENCED_MEMBER_EPOCH;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.INVALID_GROUP_ID;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NONE;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.REBALANCE_IN_PROGRESS;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN_MEMBER_ID;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.session.GroupReconciliation;
import io.camunda.eventbridge.consumergroups.session.MemberLivenessMirror;
import io.camunda.eventbridge.consumergroups.stream.CoordinatorStream;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves consumer-group heartbeats — the one coordination request that stays request/response (no
 * log write on the hot path). It owns the ephemeral assign/revoke handshake per group ({@link
 * GroupReconciliation}), reads the durable target from the coordinator stream, and publishes each
 * member's liveness to the {@link MemberLivenessMirror} for the off-actor {@code
 * SessionEvictionTask} to expire dead sessions. The only log write it makes is a fire-and-forget
 * {@code RECONCILE_MEMBER} once a member owns exactly the current target, so the group can move
 * {@code RECONCILING -> STABLE}.
 *
 * <p>Single-actor-confined: the {@link ConsumerGroupCoordinator} invokes every method on its own
 * actor, so the reconciliation map needs no synchronization. After failover, {@link #seed} rebuilds
 * the handshake from replicated state so re-attaching consumers keep their target without a rejoin
 * storm.
 */
final class HeartbeatHandler {

  private static final Logger LOG = LoggerFactory.getLogger(HeartbeatHandler.class);

  private final int partitionId;
  private final InstantSource clock;
  private final CoordinatorStream coordinatorStream;
  private final MemberLivenessMirror liveness;

  // Ephemeral reconciliation handshake per group (rebuilt from heartbeats / seed after failover).
  private final Map<String, GroupReconciliation> reconciliations = new HashMap<>();

  HeartbeatHandler(
      final int partitionId,
      final InstantSource clock,
      final CoordinatorStream coordinatorStream,
      final MemberLivenessMirror liveness) {
    this.partitionId = partitionId;
    this.clock = clock;
    this.coordinatorStream = coordinatorStream;
    this.liveness = liveness;
  }

  /**
   * Rebuilds the reconciliation sessions (and the liveness they feed) from the replicated
   * membership replayed before the coordinator started, so re-attaching consumers get fresh
   * deadlines and are treated as already at their target — no rejoin storm after a failover.
   */
  void seed() {
    final var now = clock.instant();
    final var groups = coordinatorStream.groupSnapshots();
    for (final var group : groups) {
      final var reconciliation =
          reconciliations.computeIfAbsent(group.groupId(), ignored -> new GroupReconciliation());
      group
          .members()
          .values()
          .forEach(member -> reconciliation.seedSession(member, group.assignmentEpoch(), now));
      liveness.publish(group.groupId(), reconciliation.liveness());
    }
    if (!groups.isEmpty()) {
      LOG.info(
          "Coordinator partition {} — restored {} consumer group(s) from replicated state",
          partitionId,
          groups.size());
    }
  }

  /** Serves one heartbeat: validates the member, runs the handshake, and returns the reply. */
  HeartbeatResponse handle(final HeartbeatRequest request) {
    final var groupId = request.getGroupId();
    if (groupId == null || groupId.isEmpty()) {
      return new HeartbeatResponse().setErrorCode(INVALID_GROUP_ID);
    }

    final var group = coordinatorStream.groupSnapshot(groupId);
    final var memberId = request.getMemberId();
    final var member = group == null ? null : group.members().get(memberId);
    if (member == null) {
      return new HeartbeatResponse().setErrorCode(UNKNOWN_MEMBER_ID);
    }

    final var epochError = validateEpoch(member.memberEpoch(), request.getMemberEpoch());
    if (epochError != NONE) {
      return new HeartbeatResponse().setErrorCode(epochError);
    }

    final var reconciliation =
        reconciliations.computeIfAbsent(groupId, ignored -> new GroupReconciliation());
    final var delta =
        reconciliation.reconcile(group, memberId, request.getOwnedPartitions(), clock.instant());
    // Publish the refreshed liveness for the off-actor eviction task, and drop reconciliations for
    // groups that have since disappeared (their last member left).
    liveness.publish(groupId, reconciliation.liveness());
    pruneReconciliations();

    // Record the member's convergence as replicated state once it owns exactly the current target
    // (and that hasn't been recorded yet), so the group can transition RECONCILING -> STABLE. This
    // is the only point where a heartbeat writes to the log; it's idempotent (the processor drops a
    // duplicate) and fire-and-forget — the new state is reflected on a later heartbeat.
    if (!group.isRebalancePending()
        && reconciliation.hasConverged(memberId, group.assignmentEpoch())
        && member.assignedEpoch() < group.groupEpoch()) {
      coordinatorStream.reconcileMember(
          new MembershipRecord()
              .setGroupId(groupId)
              .setMemberId(memberId)
              .setGroupEpoch(group.groupEpoch()));
    }

    return new HeartbeatResponse()
        .setErrorCode(reconciliation.isRebalancing(group) ? REBALANCE_IN_PROGRESS : NONE)
        .setMemberId(memberId)
        .setMemberEpoch(member.memberEpoch())
        .setAssign(delta.assign())
        .setRevoke(delta.revoke())
        .setAssignment(delta.assignment())
        .setAssignmentEpoch(group.assignmentEpoch())
        .setCommittedOffsets(coordinatorStream.committedOffsets(groupId));
  }

  private static CoordinationErrorCode validateEpoch(final long expected, final long presented) {
    if (expected > presented) {
      return FENCED_MEMBER_EPOCH;
    }
    if (expected != presented) {
      return UNKNOWN_MEMBER_ID;
    }
    return NONE;
  }

  /** Drops reconciliation state for groups that no longer exist (their last member left). */
  private void pruneReconciliations() {
    reconciliations.keySet().removeIf(groupId -> coordinatorStream.groupSnapshot(groupId) == null);
  }
}
