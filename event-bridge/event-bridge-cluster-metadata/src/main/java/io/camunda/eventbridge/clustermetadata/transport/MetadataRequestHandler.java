/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.transport;

import io.camunda.eventbridge.clustermetadata.MetadataManager;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsRequest;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationRequest;
import io.camunda.eventbridge.transport.RequestHandler;
import io.camunda.zeebe.msgpack.UnpackedObject;
import java.util.concurrent.CompletableFuture;

/**
 * Routes topic admin requests (create/delete/reassign/list) on the dedicated metadata Raft group to
 * the {@link MetadataManager}. Symmetric with {@code CoordinationRequestHandler} on the coordinator
 * group: each parses the {@link CoordinationRequest} and dispatches to its manager, which returns
 * the reply already framed for the broker client.
 */
public final class MetadataRequestHandler implements RequestHandler {

  /** Broker-client routing group for topic metadata — its own single-partition Raft group. */
  public static final String METADATA_ROUTING_GROUP = "event-bridge-metadata";

  // Must exactly match the subject the gateway BrokerClient sends to:
  // "<group>-<requestType>-api-<partitionId>" (see AtomixServerTransport#topicName). The gateway
  // sets the coordinate request's partition group to METADATA_ROUTING_GROUP and resolves this
  // (metadata-group) partition's leader from gossip.
  private static final String TOPIC_FORMAT = METADATA_ROUTING_GROUP + "-coordinate-api-%d";

  private final MetadataManager metadataManager;

  public MetadataRequestHandler(final int partitionId, final MetadataManager metadataManager) {
    this.metadataManager = metadataManager;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    final var request = CoordinationRequest.from(requestBytes);
    return switch (request.type()) {
      case CREATE_TOPIC -> metadataManager.handleCreateTopic(read(new CreateTopicRequest(), request));
      case DELETE_TOPIC -> metadataManager.handleDeleteTopic(read(new DeleteTopicRequest(), request));
      case REASSIGN_TOPIC ->
          metadataManager.handleReassignTopic(read(new ReassignTopicRequest(), request));
      case LIST_TOPICS -> metadataManager.handleListTopics(read(new ListTopicsRequest(), request));
      default ->
          CompletableFuture.failedFuture(
              new IllegalArgumentException("Unknown request type: " + request.type()));
    };
  }

  /** Decodes the request's value payload into the given DTO. */
  private static <T extends UnpackedObject> T read(final T dto, final CoordinationRequest request) {
    dto.wrap(request.value());
    return dto;
  }

  public static String topicName(final int partitionId) {
    return String.format(TOPIC_FORMAT, partitionId);
  }
}
