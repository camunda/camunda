/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.transport;

import io.camunda.eventbridge.clustermetadata.MetadataManager;
import io.camunda.eventbridge.clustermetadata.MetadataQueryHandler;
import io.camunda.eventbridge.clustermetadata.membership.BrokerHeartbeatHandler;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.protocol.request.coordination.BrokerHeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsRequest;
import io.camunda.eventbridge.protocol.request.coordination.RegisterBrokerRequest;
import io.camunda.eventbridge.protocol.request.coordination.ReportPartitionLeaderRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.eventbridge.stream.CommandRejectionException;
import io.camunda.eventbridge.transport.RequestHandler;
import io.camunda.zeebe.msgpack.UnpackedObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Routes topic admin requests (create/delete/reassign/list) on the dedicated metadata Raft group to
 * the {@link MetadataManager}, then frames the reply for the broker client. Symmetric with {@code
 * CoordinationRequestHandler} on the coordinator group: each parses the {@link
 * CoordinationRequest}, dispatches to its manager which returns the raw response payload (or fails
 * with a {@link CommandRejectionException}), and wraps a success in the {@code
 * ExecuteCoordinateResponse} envelope and a rejection into a {@code BrokerRejection} the gateway
 * maps to an HTTP status.
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
  private final MetadataQueryHandler metadataQueryHandler;
  private final BrokerHeartbeatHandler brokerHeartbeatHandler;

  public MetadataRequestHandler(
      final int partitionId,
      final MetadataManager metadataManager,
      final MetadataQueryHandler metadataQueryHandler,
      final BrokerHeartbeatHandler brokerHeartbeatHandler) {
    this.metadataManager = metadataManager;
    this.metadataQueryHandler = metadataQueryHandler;
    this.brokerHeartbeatHandler = brokerHeartbeatHandler;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    final var request = CoordinationRequest.from(requestBytes);
    return dispatch(request).handle(MetadataRequestHandler::frame);
  }

  /**
   * Decodes the typed request and routes it to the write path ({@link MetadataManager}) or the read
   * path ({@link MetadataQueryHandler}), which return the raw response.
   */
  private CompletableFuture<byte[]> dispatch(final CoordinationRequest request) {
    return switch (request.type()) {
      case CREATE_TOPIC -> metadataManager.handleCreateTopic(read(new TopicRecord(), request));
      case DELETE_TOPIC -> metadataManager.handleDeleteTopic(read(new TopicRecord(), request));
      case REASSIGN_TOPIC -> metadataManager.handleReassignTopic(read(new TopicRecord(), request));
      case REPORT_PARTITION_LEADER ->
          metadataManager.handleReportPartitionLeader(
              read(new ReportPartitionLeaderRequest(), request));
      case LIST_TOPICS ->
          metadataQueryHandler.handleListTopics(read(new ListTopicsRequest(), request));
      case REGISTER_BROKER ->
          brokerHeartbeatHandler.handleRegister(read(new RegisterBrokerRequest(), request));
      case BROKER_HEARTBEAT ->
          brokerHeartbeatHandler.handleHeartbeat(read(new BrokerHeartbeatRequest(), request));
      default ->
          CompletableFuture.failedFuture(
              new IllegalArgumentException("Unknown request type: " + request.type()));
    };
  }

  /**
   * Frames the manager's raw reply: a success wraps the payload in the response envelope, a {@link
   * CommandRejectionException} becomes a rejection response, and any other failure propagates (the
   * messaging layer fails the request).
   */
  private static byte[] frame(final byte[] response, final Throwable error) {
    if (error == null) {
      return CoordinationResponseEncoder.encodeValue(response);
    }
    final var cause =
        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (cause instanceof final CommandRejectionException rejection) {
      return CoordinationResponseEncoder.encodeRejection(rejection.type(), rejection.getMessage());
    }
    throw error instanceof final CompletionException completion
        ? completion
        : new CompletionException(error);
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
