/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.membership;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.session.MemberLivenessMirror;
import io.camunda.eventbridge.consumergroups.stream.CoordinatorStream;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.zeebe.scheduler.Actor;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.agrona.concurrent.SnowflakeIdGenerator;

/**
 * The leader-side coordinator for consumer-group membership requests. Membership and target
 * assignment are first-class replicated state on the coordinator stream (KIP-848 model): {@code
 * JOIN_GROUP}, {@code LEAVE_GROUP} and {@code COMMIT_OFFSET} are written as commands and validated
 * in their processors, so this manager only turns a request into a command and forwards the
 * committed reply. Heartbeats — the one request that stays request/response and runs the in-memory
 * assign/revoke handshake — are delegated to the {@link HeartbeatHandler}, which keeps the {@link
 * MemberLivenessMirror} the off-actor {@code SessionEvictionTask} reads up to date. The read-only
 * requests (offset fetch, describe) run on a separate {@link ConsumerGroupQueryHandler} actor.
 */
public class ConsumerGroupCoordinator extends Actor {

  private final int partitionId;
  private final CoordinatorStream coordinatorStream;
  private final MemberLivenessMirror liveness;
  private final HeartbeatHandler heartbeatHandler;
  private final SnowflakeIdGenerator idGenerator;

  public ConsumerGroupCoordinator(
      final int partitionId, final InstantSource clock, final CoordinatorStream coordinatorStream) {
    this.partitionId = partitionId;
    this.coordinatorStream = coordinatorStream;
    liveness = coordinatorStream.liveness();
    heartbeatHandler = new HeartbeatHandler(partitionId, clock, coordinatorStream, liveness);
    idGenerator = new SnowflakeIdGenerator(1L);
  }

  @Override
  public String getName() {
    return "ConsumerGroupCoordinator-" + partitionId;
  }

  @Override
  protected void onActorStarted() {
    // The durable membership/target was replayed into the stream's state before this manager
    // started, so seed the heartbeat handshake from it: re-attaching consumers get fresh deadlines
    // and are treated as already at their target (no rejoin storm after a failover). The eviction
    // sweep runs as a separate task on the stream's async group.
    heartbeatHandler.seed();
  }

  @Override
  protected void onActorClosing() {
    // Leadership is being given up — abandon the ephemeral liveness so the eviction task (also
    // stopping) cannot act on stale sessions; a new leader reseeds it from replicated state.
    liveness.clear();
  }

  /**
   * Writes a {@code JOIN_GROUP} command and forwards the committed {@code JoinGroupResponse}. The
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
          coordinatorStream.joinGroup(command).whenComplete(forward(result));
        });
    return result;
  }

  /** Writes a {@code LEAVE_GROUP} command and forwards the committed {@code LeaveGroupResponse}. */
  public CompletableFuture<byte[]> handleLeaveGroup(final LeaveGroupRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          final var command =
              new MembershipRecord()
                  .setGroupId(request.getGroupId())
                  .setMemberId(request.getMemberId())
                  .setMemberEpoch(request.getMemberEpoch());
          coordinatorStream.leaveGroup(command).whenComplete(forward(result));
        });
    return result;
  }

  /** Serves a heartbeat (request/response, no log write); returns the serialized reply. */
  public CompletableFuture<byte[]> handleHeartbeat(final HeartbeatRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            result.complete(
                CoordinationResponseEncoder.serialize(heartbeatHandler.handle(request)));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  /**
   * Replicates an offset commit through the coordinator stream and forwards the committed {@code
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
    actor.run(() -> coordinatorStream.commit(command).whenComplete(forward(result)));
    return result;
  }

  /**
   * Forwards the stream's raw reply (or its failure) to {@code result} as-is — no framing. The
   * {@code CoordinationRequestHandler} frames a successful payload and turns a {@code
   * CommandRejectionException} into a rejection response, so this manager returns raw responses and
   * the transport layer owns the wire envelope.
   */
  private static BiConsumer<byte[], Throwable> forward(final CompletableFuture<byte[]> result) {
    return (response, error) -> {
      if (error != null) {
        result.completeExceptionally(error);
      } else {
        result.complete(response);
      }
    };
  }

  private String generateMemberId() {
    return String.valueOf(idGenerator.nextId());
  }
}
