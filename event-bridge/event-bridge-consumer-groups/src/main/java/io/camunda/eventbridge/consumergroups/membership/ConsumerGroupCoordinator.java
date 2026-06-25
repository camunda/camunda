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
import io.camunda.eventbridge.consumergroups.stream.CoordinatorStream;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.zeebe.scheduler.Actor;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.agrona.concurrent.SnowflakeIdGenerator;

/**
 * The streaming (write) path for consumer-group membership, on its own actor. Membership and target
 * assignment are first-class replicated state on the coordinator stream (KIP-848 model): {@code
 * JOIN_GROUP}, {@code LEAVE_GROUP} and {@code COMMIT_OFFSET} are written as commands and validated
 * in their processors, so this manager only turns a request into a command and forwards the
 * stream's reply — the {@link
 * io.camunda.eventbridge.consumergroups.transport.CoordinationRequestHandler} frames it.
 *
 * <p>The log writer is thread-safe (the sequencer serializes concurrent writes), so this actor is
 * not strictly required for write safety; it exists so the three request paths are symmetric — this
 * (writes), the {@link HeartbeatHandler} (liveness), and the {@code ConsumerGroupQueryHandler}
 * (reads) each run on their own actor, with the stateful ones (heartbeat, reads) genuinely needing
 * it.
 */
public class ConsumerGroupCoordinator extends Actor {

  private final int partitionId;
  private final CoordinatorStream coordinatorStream;
  private final SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(1L);

  public ConsumerGroupCoordinator(
      final int partitionId, final CoordinatorStream coordinatorStream) {
    this.partitionId = partitionId;
    this.coordinatorStream = coordinatorStream;
  }

  @Override
  public String getName() {
    return "ConsumerGroupCoordinator-" + partitionId;
  }

  /**
   * Writes a {@code JOIN_GROUP} command and forwards the committed reply. The command carries only
   * the request's intent (group id, subscribed topic, instance id); the processor resolves the
   * topic's partition count from the registry at processing time and validates it, so the decision
   * is made by the leader that actually produces the durable event.
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

  /** Writes a {@code LEAVE_GROUP} command and forwards the committed reply. */
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

  /**
   * Replicates an offset commit through the coordinator stream and forwards the committed reply.
   * Validation (member epoch + partition ownership) happens in the {@code OffsetCommitProcessor}
   * against replicated membership, not here.
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

  /** Forwards the stream's raw reply (or its failure) to {@code result} — the handler frames it. */
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
