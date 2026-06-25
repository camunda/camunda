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
import io.camunda.zeebe.scheduler.Actor;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.agrona.concurrent.SnowflakeIdGenerator;

/**
 * The streaming (write) path for consumer-group membership, on its own actor. The command record
 * that arrives on the wire <em>is</em> what gets written to the log: the service layer (gateway)
 * already built the {@link MembershipRecord}/{@link OffsetCommitRecord}, so this manager writes it
 * straight to the coordinator stream without a request→record mapping step and forwards the reply
 * (the {@link io.camunda.eventbridge.consumergroups.transport.CoordinationRequestHandler} frames
 * it). The processors validate the command against replicated state.
 *
 * <p>The one field it sets is the server-assigned member id on a join (clients don't pick it). The
 * log writer is thread-safe, so this actor is not strictly required for write safety; it exists so
 * the three request paths are symmetric — this (writes), the {@link HeartbeatHandler} (liveness),
 * and the {@code ConsumerGroupQueryHandler} (reads) each run on their own actor.
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
   * Writes the {@code JOIN_GROUP} command (built by the service layer) after stamping a freshly
   * minted member id, and forwards the committed reply. The processor resolves the topic's
   * partition count from the registry at processing time and validates it, so the decision is made
   * by the leader that produces the durable event.
   */
  public CompletableFuture<byte[]> handleJoinGroup(final MembershipRecord command) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          command.setMemberId(generateMemberId());
          coordinatorStream.joinGroup(command).whenComplete(forward(result));
        });
    return result;
  }

  /** Writes the {@code LEAVE_GROUP} command as received and forwards the committed reply. */
  public CompletableFuture<byte[]> handleLeaveGroup(final MembershipRecord command) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(() -> coordinatorStream.leaveGroup(command).whenComplete(forward(result)));
    return result;
  }

  /**
   * Replicates the offset-commit command as received and forwards the committed reply. Validation
   * (member epoch + partition ownership) happens in the {@code OffsetCommitProcessor} against
   * replicated membership, not here.
   */
  public CompletableFuture<byte[]> handleCommit(final OffsetCommitRecord command) {
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
