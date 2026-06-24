/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.transport;

import io.camunda.eventbridge.consumergroups.coordination.CoordinationManager;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.eventbridge.transport.RequestHandler;
import java.util.concurrent.CompletableFuture;

/**
 * Handles consumer-group coordination requests (join/heartbeat/leave/commit) on the coordinator
 * Raft group. Topic admin (create/delete/reassign/list) is served by {@link MetadataRequestHandler}
 * on the separate metadata Raft group.
 */
public final class CoordinationRequestHandler implements RequestHandler {

  /** Broker-client routing group for coordination — distinct from the data group ("default"). */
  public static final String COORDINATOR_ROUTING_GROUP = "event-bridge-coordinator";

  // Must exactly match the subject the gateway BrokerClient sends to:
  // "<group>-<requestType>-api-<partitionId>" (see AtomixServerTransport#topicName). The gateway
  // sets the coordinate request's partition group to COORDINATOR_ROUTING_GROUP and resolves this
  // (coordinator-group) partition's leader from gossip.
  private static final String TOPIC_FORMAT = COORDINATOR_ROUTING_GROUP + "-coordinate-api-%d";

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

    // The coordinator stream replies with the serialized CommitOffsetResponse once the command has
    // been processed and committed (validation + response now happen in the processor); frame it in
    // the ExecuteCoordinateResponse envelope the gateway's broker client decodes.
    return coordinationManager
        .handleCommit(commit)
        .thenApply(CoordinationResponseEncoder::encodeValue);
  }

  private CompletableFuture<byte[]> handleJoinGroup(final CoordinationRequest request) {
    final var joinGroup = new JoinGroupRequest();
    joinGroup.wrap(request.value());

    // Join now replies through the stream after the command commits (the processor assigns the
    // member id/epoch and stages the response); frame the serialized reply for the broker client.
    return coordinationManager
        .handleJoinGroup(joinGroup)
        .thenApply(CoordinationResponseEncoder::encodeValue);
  }

  private CompletableFuture<byte[]> handleLeaveGroup(final CoordinationRequest request) {
    final var leaveGroup = new LeaveGroupRequest();
    leaveGroup.wrap(request.value());

    return coordinationManager
        .handleLeaveGroup(leaveGroup)
        .thenApply(CoordinationResponseEncoder::encodeValue);
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
