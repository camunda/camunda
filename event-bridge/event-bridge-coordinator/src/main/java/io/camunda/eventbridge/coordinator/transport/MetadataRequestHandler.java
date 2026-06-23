/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.transport;

import io.camunda.eventbridge.coordinator.MetadataManager;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsRequest;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicRequest;
import io.camunda.eventbridge.transport.RequestHandler;
import java.util.concurrent.CompletableFuture;

/**
 * Handles topic admin requests (create/delete/reassign/list) on the dedicated metadata Raft group.
 * Consumer-group coordination (join/heartbeat/leave/commit) is served by {@link
 * CoordinationRequestHandler} on the coordinator Raft group.
 */
public final class MetadataRequestHandler implements RequestHandler {

  /** Broker-client routing group for topic metadata — its own single-partition Raft group. */
  public static final String METADATA_ROUTING_GROUP = "event-bridge-metadata";

  // Must exactly match the subject the gateway BrokerClient sends to:
  // "<group>-<requestType>-api-<partitionId>" (see AtomixServerTransport#topicName). The gateway
  // sets the coordinate request's partition group to METADATA_ROUTING_GROUP and resolves this
  // (metadata-group) partition's leader from gossip.
  private static final String TOPIC_FORMAT = METADATA_ROUTING_GROUP + "-coordinate-api-%d";

  private final int partitionId;
  private final MetadataManager metadataManager;

  public MetadataRequestHandler(final int partitionId, final MetadataManager metadataManager) {
    this.partitionId = partitionId;
    this.metadataManager = metadataManager;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    final var request = CoordinationRequest.from(requestBytes);

    return switch (request.type()) {
      case CREATE_TOPIC -> handleCreateTopic(request);
      case DELETE_TOPIC -> handleDeleteTopic(request);
      case REASSIGN_TOPIC -> handleReassignTopic(request);
      case LIST_TOPICS -> handleListTopics(request);
      default ->
          CompletableFuture.failedFuture(
              new IllegalArgumentException("Unknown request type: " + request.type()));
    };
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
