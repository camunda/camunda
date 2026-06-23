/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.transport;

import io.camunda.eventbridge.coordinator.CoordinationManager;
import io.camunda.eventbridge.coordinator.MetadataManager;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsRequest;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicRequest;
import io.camunda.eventbridge.transport.RequestHandler;
import java.util.concurrent.CompletableFuture;

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
  private final MetadataManager metadataManager;

  public CoordinationRequestHandler(
      final int partitionId,
      final CoordinationManager coordinationManager,
      final MetadataManager metadataManager) {
    this.partitionId = partitionId;
    this.coordinationManager = coordinationManager;
    this.metadataManager = metadataManager;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    final var request = CoordinationRequest.from(requestBytes);

    return switch (request.type()) {
      case JOIN_GROUP -> handleJoinGroup(request);
      case LEAVE_GROUP -> handleLeaveGroup(request);
      case HEARTBEAT -> handleHeartbeat(request);
      case COMMIT -> handleCommit(request);
      case CREATE_TOPIC -> handleCreateTopic(request);
      case DELETE_TOPIC -> handleDeleteTopic(request);
      case REASSIGN_TOPIC -> handleReassignTopic(request);
      case LIST_TOPICS -> handleListTopics(request);
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

  private CompletableFuture<byte[]> handleCreateTopic(final CoordinationRequest request) {
    final var create = new CreateTopicRequest();
    create.wrap(request.value());
    return toFuture(metadataManager.handleCreateTopic(create), CoordinationResponseEncoder::encode);
  }

  private CompletableFuture<byte[]> handleDeleteTopic(final CoordinationRequest request) {
    final var delete = new DeleteTopicRequest();
    delete.wrap(request.value());
    return toFuture(metadataManager.handleDeleteTopic(delete), CoordinationResponseEncoder::encode);
  }

  private CompletableFuture<byte[]> handleReassignTopic(final CoordinationRequest request) {
    final var reassign = new ReassignTopicRequest();
    reassign.wrap(request.value());
    return toFuture(
        metadataManager.handleReassignTopic(reassign), CoordinationResponseEncoder::encode);
  }

  private CompletableFuture<byte[]> handleListTopics(final CoordinationRequest request) {
    final var list = new ListTopicsRequest();
    list.wrap(request.value());
    return toFuture(metadataManager.handleListTopics(list), CoordinationResponseEncoder::encode);
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
