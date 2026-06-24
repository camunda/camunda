/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.transport;

import io.camunda.eventbridge.consumergroups.membership.ConsumerGroupCoordinator;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.transport.CoordinationRequest;
import io.camunda.eventbridge.transport.RequestHandler;
import io.camunda.zeebe.msgpack.UnpackedObject;
import java.util.concurrent.CompletableFuture;

/**
 * Routes consumer-group coordination requests (join/heartbeat/leave/commit) on the coordinator Raft
 * group to the {@link ConsumerGroupCoordinator}. Topic admin is served by {@code
 * MetadataRequestHandler} on the metadata Raft group; the two handlers are deliberately symmetric:
 * each parses the {@link CoordinationRequest} and dispatches to its manager, which returns the
 * reply already framed for the broker client.
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

  public CoordinationRequestHandler(
      final int partitionId, final ConsumerGroupCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    final var request = CoordinationRequest.from(requestBytes);
    return switch (request.type()) {
      case JOIN_GROUP -> coordinator.handleJoinGroup(read(new JoinGroupRequest(), request));
      case LEAVE_GROUP -> coordinator.handleLeaveGroup(read(new LeaveGroupRequest(), request));
      case HEARTBEAT -> coordinator.handleHeartbeat(read(new HeartbeatRequest(), request));
      case COMMIT -> coordinator.handleCommit(read(new CommitOffsetRequest(), request));
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
