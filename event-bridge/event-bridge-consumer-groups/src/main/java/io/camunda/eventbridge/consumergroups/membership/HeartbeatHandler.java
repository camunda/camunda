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
import io.camunda.eventbridge.consumergroups.state.group.ConsumerGroupQueryService;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot.MemberSnapshot;
import io.camunda.eventbridge.consumergroups.state.offset.OffsetQueryService;
import io.camunda.eventbridge.consumergroups.stream.CoordinatorStream;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.zeebe.scheduler.Actor;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves consumer-group heartbeats on its own actor — the liveness path, kept separate from the
 * coordinator's streaming (write) path. It owns the ephemeral assign/revoke handshake per group
 * ({@link GroupReconciliation}), reads the durable target through its <em>own</em> {@link
 * ConsumerGroupQueryService} / {@link OffsetQueryService} (private contexts, so no flyweights are
 * shared with the coordinator or the query handler), and publishes each member's liveness to the
 * {@link MemberLivenessMirror} for the off-actor {@code SessionEvictionTask} to expire dead
 * sessions. Its one log write is a fire-and-forget {@code RECONCILE_MEMBER} once a member owns
 * exactly the current target, so the group can move {@code RECONCILING -> STABLE} (the log writer
 * is thread-safe, so this is safe alongside the coordinator's writes).
 *
 * <p>On leader activation {@link #onActorStarted} rebuilds the handshake from replicated state so
 * re-attaching consumers keep their target without a rejoin storm.
 */
public final class HeartbeatHandler extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(HeartbeatHandler.class);

  private final int partitionId;
  private final InstantSource clock;
  private final CoordinatorStream coordinatorStream;
  private final ConsumerGroupQueryService groupQuery;
  private final OffsetQueryService offsetQuery;
  private final MemberLivenessMirror liveness;

  // Ephemeral reconciliation handshake per group (rebuilt from heartbeats / seed after failover).
  private final Map<String, GroupReconciliation> reconciliations = new HashMap<>();

  public HeartbeatHandler(
      final int partitionId, final InstantSource clock, final CoordinatorStream coordinatorStream) {
    this.partitionId = partitionId;
    this.clock = clock;
    this.coordinatorStream = coordinatorStream;
    groupQuery = coordinatorStream.newGroupQueryService();
    offsetQuery = coordinatorStream.newOffsetQueryService();
    liveness = coordinatorStream.liveness();
  }

  @Override
  public String getName() {
    return "HeartbeatHandler-" + partitionId;
  }

  @Override
  protected void onActorStarted() {
    seed();
  }

  @Override
  protected void onActorClosing() {
    // Leadership is being given up — abandon the ephemeral liveness so the eviction task (also
    // stopping) cannot act on stale sessions; a new leader reseeds it from replicated state.
    liveness.clear();
  }

  /** Serves one heartbeat and returns the serialized reply (the request handler frames it). */
  public CompletableFuture<byte[]> handleHeartbeat(final HeartbeatRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            result.complete(CoordinationResponseEncoder.serialize(heartbeat(request)));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  /**
   * Rebuilds the reconciliation sessions (and the liveness they feed) from the replicated
   * membership replayed before this actor started, so re-attaching consumers get fresh deadlines
   * and are treated as already at their target — no rejoin storm after a failover.
   */
  private void seed() {
    final var now = clock.instant();
    final var groups = groupQuery.allGroups();
    for (final var group : groups) {
      final var reconciliation = reconciliationFor(group.groupId());
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

  private HeartbeatResponse heartbeat(final HeartbeatRequest request) {
    final var groupId = request.getGroupId();
    if (groupId == null || groupId.isEmpty()) {
      return new HeartbeatResponse().setErrorCode(INVALID_GROUP_ID);
    }

    final var group = groupQuery.groupSnapshot(groupId);
    final var memberId = request.getMemberId();
    final var member = group == null ? null : group.members().get(memberId);
    if (member == null) {
      return new HeartbeatResponse().setErrorCode(UNKNOWN_MEMBER_ID);
    }

    final var epochError = validateEpoch(member.memberEpoch(), request.getMemberEpoch());
    if (epochError != NONE) {
      return new HeartbeatResponse().setErrorCode(epochError);
    }

    final var reconciliation = reconciliationFor(groupId);
    final var delta =
        reconciliation.reconcile(group, memberId, request.getOwnedPartitions(), clock.instant());
    // Publish the refreshed liveness for the off-actor eviction task, and drop reconciliations for
    // groups that have since disappeared (their last member left).
    liveness.publish(groupId, reconciliation.liveness());
    pruneReconciliations();
    maybeRecordConvergence(group, member, reconciliation);

    return new HeartbeatResponse()
        .setErrorCode(reconciliation.isRebalancing(group) ? REBALANCE_IN_PROGRESS : NONE)
        .setMemberId(memberId)
        .setMemberEpoch(member.memberEpoch())
        .setAssign(delta.assign())
        .setRevoke(delta.revoke())
        .setAssignment(delta.assignment())
        .setAssignmentEpoch(group.assignmentEpoch())
        .setCommittedOffsets(offsetQuery.committedOffsets(groupId));
  }

  private GroupReconciliation reconciliationFor(final String groupId) {
    return reconciliations.computeIfAbsent(groupId, ignored -> new GroupReconciliation());
  }

  /**
   * Records the member's convergence in replicated state once it owns exactly the current target
   * (and that hasn't been recorded yet), so the group can transition {@code RECONCILING -> STABLE}.
   * This is the only point where a heartbeat writes to the log; it's idempotent (the processor
   * drops a duplicate) and fire-and-forget — the new state is reflected on a later heartbeat.
   */
  private void maybeRecordConvergence(
      final GroupSnapshot group,
      final MemberSnapshot member,
      final GroupReconciliation reconciliation) {
    if (group.isRebalancePending()
        || !reconciliation.hasConverged(member.memberId(), group.assignmentEpoch())
        || member.assignedEpoch() >= group.groupEpoch()) {
      return;
    }
    coordinatorStream.reconcileMember(
        new MembershipRecord()
            .setGroupId(group.groupId())
            .setMemberId(member.memberId())
            .setGroupEpoch(group.groupEpoch()));
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
    reconciliations.keySet().removeIf(groupId -> groupQuery.groupSnapshot(groupId) == null);
  }
}
