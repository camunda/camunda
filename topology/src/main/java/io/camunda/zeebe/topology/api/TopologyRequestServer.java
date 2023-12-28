/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.topology.serializer.TopologyRequestsSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.util.Either;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

/** Server that receives the topology management requests */
public final class TopologyRequestServer implements AutoCloseable {

  private final TopologyManagementApi topologyManagementApi;
  private final ClusterCommunicationService communicationService;
  private final ConcurrencyControl executor;
  private final TopologyRequestsSerializer serializer;

  public TopologyRequestServer(
      final ClusterCommunicationService communicationService,
      final TopologyRequestsSerializer serializer,
      final TopologyManagementApi topologyManagementApi,
      final ConcurrencyControl executor) {
    this.topologyManagementApi = topologyManagementApi;
    this.communicationService = communicationService;
    this.serializer = serializer;
    this.executor = executor;
  }

  public void start() {
    registerAddMemberRequestsHandler();
    registerRemoveMemberRequestsHandler();
    registerJoinPartitionRequestsHandler();
    registerLeavePartitionRequestsHandler();
    registerReassignPartitionRequestHandler();
    registerScaleRequestHandler();
    registerGetTopologyQueryHandler();
    registerTopologyCancelHandler();
  }

  @Override
  public void close() {
    Stream.of(TopologyRequestTopics.values())
        .toList()
        .forEach(topic -> communicationService.unsubscribe(topic.topic()));
  }

  private void registerAddMemberRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.ADD_MEMBER.topic(),
        serializer::decodeAddMembersRequest,
        request -> mapResponse(topologyManagementApi.addMembers(request)),
        this::encodeResponse);
  }

  byte[] encodeResponse(final Either<ErrorResponse, TopologyChangeResponse> response) {
    if (response.isLeft()) {
      return serializer.encodeResponse(response.getLeft());
    } else {
      return serializer.encodeResponse(response.get());
    }
  }

  byte[] encodeClusterTopologyResponse(final Either<ErrorResponse, ClusterTopology> response) {
    if (response.isLeft()) {
      return serializer.encodeResponse(response.getLeft());
    } else {
      return serializer.encodeResponse(response.get());
    }
  }

  private void registerRemoveMemberRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.REMOVE_MEMBER.topic(),
        serializer::decodeRemoveMembersRequest,
        request -> mapResponse(topologyManagementApi.removeMembers(request)),
        this::encodeResponse);
  }

  private void registerJoinPartitionRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.JOIN_PARTITION.topic(),
        serializer::decodeJoinPartitionRequest,
        request -> mapResponse(topologyManagementApi.joinPartition(request)),
        this::encodeResponse);
  }

  private void registerLeavePartitionRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.LEAVE_PARTITION.topic(),
        serializer::decodeLeavePartitionRequest,
        request -> mapResponse(topologyManagementApi.leavePartition(request)),
        this::encodeResponse);
  }

  private void registerReassignPartitionRequestHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.REASSIGN_PARTITIONS.topic(),
        serializer::decodeReassignPartitionsRequest,
        request -> mapResponse(topologyManagementApi.reassignPartitions(request)),
        this::encodeResponse);
  }

  private void registerScaleRequestHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.SCALE_MEMBERS.topic(),
        serializer::decodeScaleRequest,
        request -> mapResponse(topologyManagementApi.scaleMembers(request)),
        this::encodeResponse);
  }

  private void registerGetTopologyQueryHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.QUERY_TOPOLOGY.topic(),
        Function.identity(),
        request -> mapClusterTopologyResponse(topologyManagementApi.getTopology()),
        this::encodeClusterTopologyResponse);
  }

  private void registerTopologyCancelHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.CANCEL_CHANGE.topic(),
        serializer::decodeCancelChangeRequest,
        request -> mapClusterTopologyResponse(topologyManagementApi.cancelTopologyChange(request)),
        this::encodeClusterTopologyResponse);
  }

  private CompletableFuture<Either<ErrorResponse, TopologyChangeResponse>> mapResponse(
      final ActorFuture<TopologyChangeResponse> topologyManagementApi) {
    return topologyManagementApi
        .toCompletableFuture()
        .thenApply(Either::<ErrorResponse, TopologyChangeResponse>right)
        .exceptionally(TopologyRequestServer::mapError);
  }

  private CompletableFuture<Either<ErrorResponse, ClusterTopology>> mapClusterTopologyResponse(
      final ActorFuture<ClusterTopology> topologyManagementApi) {
    return topologyManagementApi
        .toCompletableFuture()
        .thenApply(Either::<ErrorResponse, ClusterTopology>right)
        .exceptionally(TopologyRequestServer::mapError);
  }

  private static <T> Either<ErrorResponse, T> mapError(final Throwable throwable) {
    // throwable is always CompletionException
    return switch (throwable.getCause()) {
      case final TopologyRequestFailedException.OperationNotAllowed operationNotAllowed ->
          Either.left(
              new ErrorResponse(ErrorCode.OPERATION_NOT_ALLOWED, operationNotAllowed.getMessage()));
      case final TopologyRequestFailedException.InvalidRequest invalidRequest ->
          Either.left(new ErrorResponse(ErrorCode.INVALID_REQUEST, invalidRequest.getMessage()));
      case final ConcurrentModificationException concurrentModificationException ->
          Either.left(
              new ErrorResponse(
                  ErrorCode.CONCURRENT_MODIFICATION, concurrentModificationException.getMessage()));
      default -> Either.left(new ErrorResponse(ErrorCode.INTERNAL_ERROR, throwable.getMessage()));
    };
  }
}
