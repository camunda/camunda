/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport.coordinator;

import io.camunda.eventbridge.broker.coordinator.CoordinationManager;
import io.camunda.eventbridge.broker.transport.RequestHandler;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import java.util.concurrent.CompletableFuture;

public final class CoordinationRequestHandler implements RequestHandler {

  // Must match the topic the gateway BrokerClient sends to (default partition group).
  private static final String TOPIC_FORMAT = "default-coordinate-api-%d";

  private final int partitionId;
  private final CoordinationManager coordinationManager;

  public CoordinationRequestHandler(
      final int partitionId, final CoordinationManager coordinationManager) {
    this.partitionId = partitionId;
    this.coordinationManager = coordinationManager;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    final var request = CoordinationRequest.from(requestBytes);

    return switch (request.type()) {
      case JOIN_GROUP -> handleJoinGroup(request);
      case LEAVE_GROUP -> handleLeaveGroup(request);
      case HEARTBEAT -> handleHeartbeat(request);
      case COMMIT -> handleCommit(request);
      default ->
          CompletableFuture.failedFuture(
              new IllegalArgumentException("Unknown request type: " + request.type()));
    };
  }

  private CompletableFuture<byte[]> handleCommit(final CoordinationRequest request) {
    final var commit = new CommitOffsetRequest();
    commit.wrap(request.value());

    return toFuture(
        coordinationManager.handleCommit(commit), CoordinationResponseEncoder::encodeCommit);
  }

  private CompletableFuture<byte[]> handleJoinGroup(final CoordinationRequest request) {
    final var joinGroup = new JoinGroupRequest();
    joinGroup.wrap(request.value());

    return toFuture(
        coordinationManager.handleJoinGroup(joinGroup),
        CoordinationResponseEncoder::encodeJoinGroup);
  }

  private CompletableFuture<byte[]> handleLeaveGroup(final CoordinationRequest request) {
    final var leaveGroup = new LeaveGroupRequest();
    leaveGroup.wrap(request.value());

    return toFuture(
        coordinationManager.handleLeaveGroup(leaveGroup),
        CoordinationResponseEncoder::encodeLeaveGroup);
  }

  private CompletableFuture<byte[]> handleHeartbeat(final CoordinationRequest request) {
    final var heartbeat = new HeartbeatRequest();
    heartbeat.wrap(request.value());

    return toFuture(
        coordinationManager.handleHeartbeat(heartbeat),
        CoordinationResponseEncoder::encodeHeartbeat);
  }

  /**
   * Bridges an {@link io.camunda.zeebe.scheduler.future.ActorFuture} to a {@link
   * CompletableFuture}, encoding the response with the given encoder.
   */
  private <T> CompletableFuture<byte[]> toFuture(
      final io.camunda.zeebe.scheduler.future.ActorFuture<T> actorFuture,
      final java.util.function.Function<T, byte[]> encoder) {

    final var result = new CompletableFuture<byte[]>();

    actorFuture.onComplete(
        (response, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(encoder.apply(response));
          }
        });

    return result;
  }

  public static String topicName(final int partitionId) {
    return String.format(TOPIC_FORMAT, partitionId);
  }
}
