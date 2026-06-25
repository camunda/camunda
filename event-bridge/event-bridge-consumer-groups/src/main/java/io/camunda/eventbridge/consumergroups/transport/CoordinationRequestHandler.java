/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.transport;

import io.camunda.eventbridge.consumergroups.membership.ConsumerGroupCoordinator;
import io.camunda.eventbridge.consumergroups.membership.ConsumerGroupQueryHandler;
import io.camunda.eventbridge.consumergroups.membership.HeartbeatHandler;
import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.protocol.request.coordination.DescribeGroupsRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.eventbridge.stream.CommandRejectionException;
import io.camunda.eventbridge.transport.RequestHandler;
import io.camunda.zeebe.msgpack.UnpackedObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Routes consumer-group coordination requests (join/heartbeat/leave/commit) on the coordinator Raft
 * group to the {@link ConsumerGroupCoordinator}, then frames the reply for the broker client —
 * mirroring the engine's {@code AsyncApiRequestHandler}, which owns request decoding and response
 * framing so the business code returns raw results. The coordinator returns the raw response
 * payload (or fails with a {@link CommandRejectionException}); this handler wraps a success in the
 * {@code ExecuteCoordinateResponse} envelope and a rejection into a {@code BrokerRejection} the
 * gateway maps to an HTTP status. Topic admin is served by {@code MetadataRequestHandler} on the
 * metadata Raft group.
 */
public final class CoordinationRequestHandler implements RequestHandler {

  /** Broker-client routing group for coordination — distinct from the data group ("default"). */
  public static final String COORDINATOR_ROUTING_GROUP = "event-bridge-coordinator";

  // Must exactly match the subject the gateway BrokerClient sends to:
  // "<group>-<requestType>-api-<partitionId>" (see AtomixServerTransport#topicName). The gateway
  // sets the coordinate request's partition group to COORDINATOR_ROUTING_GROUP and resolves this
  // (coordinator-group) partition's leader from gossip.
  private static final String TOPIC_FORMAT = COORDINATOR_ROUTING_GROUP + "-coordinate-api-%d";

  private final ConsumerGroupCoordinator coordinator;
  private final HeartbeatHandler heartbeatHandler;
  private final ConsumerGroupQueryHandler queryHandler;

  public CoordinationRequestHandler(
      final int partitionId,
      final ConsumerGroupCoordinator coordinator,
      final HeartbeatHandler heartbeatHandler,
      final ConsumerGroupQueryHandler queryHandler) {
    this.coordinator = coordinator;
    this.heartbeatHandler = heartbeatHandler;
    this.queryHandler = queryHandler;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    final var request = CoordinationRequest.from(requestBytes);
    return dispatch(request).handle(CoordinationRequestHandler::frame);
  }

  /** Decodes the typed request and routes it to the coordinator, which returns the raw response. */
  private CompletableFuture<byte[]> dispatch(final CoordinationRequest request) {
    return switch (request.type()) {
      case JOIN_GROUP -> coordinator.handleJoinGroup(read(new MembershipRecord(), request));
      case LEAVE_GROUP -> coordinator.handleLeaveGroup(read(new MembershipRecord(), request));
      case HEARTBEAT -> heartbeatHandler.handleHeartbeat(read(new HeartbeatRequest(), request));
      case COMMIT -> coordinator.handleCommit(read(new OffsetCommitRecord(), request));
      case OFFSET_FETCH -> queryHandler.handleOffsetFetch(read(new OffsetFetchRequest(), request));
      case DESCRIBE_GROUPS ->
          queryHandler.handleDescribeGroups(read(new DescribeGroupsRequest(), request));
      default ->
          CompletableFuture.failedFuture(
              new IllegalArgumentException("Unknown request type: " + request.type()));
    };
  }

  /**
   * Frames the coordinator's raw reply: a success wraps the payload in the response envelope, a
   * {@link CommandRejectionException} becomes a rejection response, and any other failure
   * propagates (the messaging layer fails the request).
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
