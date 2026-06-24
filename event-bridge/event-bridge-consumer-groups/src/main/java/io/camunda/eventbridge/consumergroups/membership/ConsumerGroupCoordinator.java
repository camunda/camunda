/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.membership;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.INVALID_GROUP_ID;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NONE;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.REBALANCE_IN_PROGRESS;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN_MEMBER_ID;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.session.GroupReconciliation;
import io.camunda.eventbridge.consumergroups.session.MemberLivenessMirror;
import io.camunda.eventbridge.consumergroups.stream.CoordinatorStream;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchRequest;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchResponse;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.eventbridge.stream.CommandRejectionException;
import io.camunda.zeebe.scheduler.Actor;
import java.time.InstantSource;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The leader-side coordinator for consumer-group requests. Membership and target assignment are
 * first-class replicated state on the coordinator stream (KIP-848 model): {@code JOIN_GROUP} and
 * {@code LEAVE_GROUP} are written as commands and validated in their processors, so this manager
 * only turns a request into a command and bridges the committed reply. Heartbeats stay
 * request/response — they read the durable target from the thread-safe mirror and run the
 * assign/revoke handshake in memory ({@link GroupReconciliation}), writing no log entry. After each
 * heartbeat it publishes the group's liveness to the {@link MemberLivenessMirror}; the off-actor
 * {@code SessionEvictionTask} reads that to expire dead sessions (writing a {@code LEAVE_GROUP}),
 * so this manager no longer runs an eviction timer itself.
 */
public class ConsumerGroupCoordinator extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(ConsumerGroupCoordinator.class);

  private final int partitionId;
  private final InstantSource clock;
  private final CoordinatorStream coordinatorStream;
  private final MemberLivenessMirror liveness;
  private final SnowflakeIdGenerator idGenerator;

  // Ephemeral reconciliation handshake per group (rebuilt from heartbeats after failover).
  private final Map<String, GroupReconciliation> reconciliations = new ConcurrentHashMap<>();

  public ConsumerGroupCoordinator(
      final int partitionId, final InstantSource clock, final CoordinatorStream coordinatorStream) {
    this.partitionId = partitionId;
    this.clock = clock;
    this.coordinatorStream = coordinatorStream;
    liveness = coordinatorStream.liveness();
    idGenerator = new SnowflakeIdGenerator(1L);
  }

  @Override
  public String getName() {
    return "ConsumerGroupCoordinator-" + partitionId;
  }

  @Override
  protected void onActorStarted() {
    // The durable membership/target was replayed into the stream's state (and mirror) before this
    // manager started, so seed reconciliation sessions from it: re-attaching consumers get fresh
    // deadlines and are treated as already at their target (no rejoin storm after a failover). The
    // eviction sweep runs as a separate task on the stream's async group; this manager only keeps
    // the liveness mirror it reads up to date.
    seedReconciliations();
  }

  @Override
  protected void onActorClosing() {
    // Leadership is being given up — abandon the ephemeral liveness so the eviction task (also
    // stopping) cannot act on stale sessions; a new leader reseeds it from replicated state.
    liveness.clear();
  }

  /**
   * Writes a {@code JOIN_GROUP} command and bridges the committed {@code JoinGroupResponse}. The
   * command carries only the request's intent (group id, subscribed topic, instance id); the
   * processor resolves the topic's partition count from the registry at processing time and
   * validates it, so the decision is made by the leader that actually produces the durable event.
   */
  public CompletableFuture<byte[]> handleJoinGroup(final JoinGroupRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          final var command =
              new MembershipRecord()
                  .setGroupId(request.getGroupId())
                  .setTopics(request.getTopics())
                  .setMemberId(generateMemberId())
                  .setInstanceId(request.getInstanceId());
          coordinatorStream.joinGroup(command).whenComplete(bridge(result));
        });
    return result;
  }

  /** Writes a {@code LEAVE_GROUP} command and bridges the committed {@code LeaveGroupResponse}. */
  public CompletableFuture<byte[]> handleLeaveGroup(final LeaveGroupRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          final var command =
              new MembershipRecord()
                  .setGroupId(request.getGroupId())
                  .setMemberId(request.getMemberId())
                  .setMemberEpoch(request.getMemberEpoch());
          coordinatorStream.leaveGroup(command).whenComplete(bridge(result));
        });
    return result;
  }

  /**
   * Serves an offset fetch (request/response, no log write): reads the group's committed offsets
   * from the thread-safe mirror off the processing actor — the read-from-state path, not a command
   * through the stream — and returns the framed reply.
   */
  public CompletableFuture<byte[]> handleOffsetFetch(final OffsetFetchRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            final var groupId = request.getGroupId();
            final var response = new OffsetFetchResponse();
            if (groupId == null || groupId.isEmpty()) {
              response.setErrorCode(INVALID_GROUP_ID);
            } else {
              response
                  .setErrorCode(NONE)
                  .setCommittedOffsets(coordinatorStream.committedOffsets(groupId));
            }
            result.complete(CoordinationResponseEncoder.encodeOffsetFetch(response));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  /** Serves a heartbeat (request/response, no log write) and returns the framed reply. */
  public CompletableFuture<byte[]> handleHeartbeat(final HeartbeatRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            result.complete(CoordinationResponseEncoder.encodeHeartbeat(heartbeat(request)));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  /**
   * Replicates an offset commit through the coordinator stream and returns the encoded {@code
   * CommitOffsetResponse}. Validation (member epoch + partition ownership) happens in the {@code
   * OffsetCommitProcessor} against replicated membership, not here.
   */
  public CompletableFuture<byte[]> handleCommit(final CommitOffsetRequest request) {
    final var command =
        new OffsetCommitRecord()
            .setGroupId(request.getGroupId())
            .setTopic(request.getTopic())
            .setMemberId(request.getMemberId())
            .setMemberEpoch(request.getMemberEpoch())
            .setPartitionId(request.getPartitionId())
            .setOffset(request.getPosition());

    final var result = new CompletableFuture<byte[]>();
    actor.run(() -> coordinatorStream.commit(command).whenComplete(bridge(result)));
    return result;
  }

  private HeartbeatResponse heartbeat(final HeartbeatRequest request) {
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

  private CoordinationErrorCode validateEpoch(final long expected, final long presented) {
    if (expected > presented) {
      return CoordinationErrorCode.FENCED_MEMBER_EPOCH;
    }
    if (expected != presented) {
      return UNKNOWN_MEMBER_ID;
    }
    return NONE;
  }

  private void seedReconciliations() {
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

  /** Drops reconciliation state for groups that no longer exist (their last member left). */
  private void pruneReconciliations() {
    reconciliations.keySet().removeIf(groupId -> coordinatorStream.groupSnapshot(groupId) == null);
  }

  /**
   * Completes {@code result} with the committed stream reply, framed in the {@code
   * ExecuteCoordinateResponse} envelope the gateway's broker client decodes — so every handler
   * method returns ready-to-send bytes and the transport layer only routes.
   *
   * <p>A rejected command (the processor wrote a {@code COMMAND_REJECTION} reply, surfaced here as
   * a {@link CommandRejectionException}) is framed as a <em>rejection</em> response, not completed
   * exceptionally: the gateway decodes it into a {@code BrokerRejection} and maps it to an HTTP
   * status. A genuine transport/processing failure still completes exceptionally.
   */
  private BiConsumer<byte[], Throwable> bridge(final CompletableFuture<byte[]> result) {
    return (response, error) -> {
      if (error == null) {
        result.complete(CoordinationResponseEncoder.encodeValue(response));
        return;
      }
      final var cause =
          error instanceof CompletionException && error.getCause() != null
              ? error.getCause()
              : error;
      if (cause instanceof final CommandRejectionException rejection) {
        result.complete(
            CoordinationResponseEncoder.encodeRejection(rejection.type(), rejection.getMessage()));
      } else {
        result.completeExceptionally(error);
      }
    };
  }

  private String generateMemberId() {
    return String.valueOf(idGenerator.nextId());
  }
}
