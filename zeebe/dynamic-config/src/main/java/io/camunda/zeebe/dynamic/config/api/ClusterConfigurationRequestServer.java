/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationRequestsSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

/** Server that receives the configuration management requests */
public final class ClusterConfigurationRequestServer implements AutoCloseable {

  private final ClusterConfigurationManagementApi clusterConfigurationManagementApi;
  private final ClusterCommunicationService communicationService;
  private final ClusterConfigurationRequestsSerializer serializer;

  public ClusterConfigurationRequestServer(
      final ClusterCommunicationService communicationService,
      final ClusterConfigurationRequestsSerializer serializer,
      final ClusterConfigurationManagementApi configurationManagementApi) {
    clusterConfigurationManagementApi = configurationManagementApi;
    this.communicationService = communicationService;
    this.serializer = serializer;
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
    registerForceScaleDownHandler();
    registerDisableExporterHandler();
    registerEnableExporterHandler();
    registerDeleteExporterHandler();
    registerClusterScaleRequestHandler();
    registerClusterPatchRequestHandler();
    registerUpdateRoutingStateHandler();
    registerForceRemoveBrokersRequestHandler();
    registerPurgeRequestHandler();
  }

  @Override
  public void close() {
    Stream.of(ClusterConfigurationRequestTopics.values())
        .toList()
        .forEach(topic -> communicationService.unsubscribe(topic.topic()));
  }

  private void registerAddMemberRequestsHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.ADD_MEMBER.topic(),
        serializer::decodeAddMembersRequest,
        request -> mapResponse(clusterConfigurationManagementApi.addMembers(request)),
        this::encodeResponse);
  }

  byte[] encodeResponse(final Either<ErrorResponse, ClusterConfigurationChangeResponse> response) {
    if (response.isLeft()) {
      return serializer.encodeResponse(response.getLeft());
    } else {
      return serializer.encodeResponse(response.get());
    }
  }

  byte[] encodeClusterTopologyResponse(final Either<ErrorResponse, ClusterConfiguration> response) {
    if (response.isLeft()) {
      return serializer.encodeResponse(response.getLeft());
    } else {
      return serializer.encodeResponse(response.get());
    }
  }

  private void registerRemoveMemberRequestsHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.REMOVE_MEMBER.topic(),
        serializer::decodeRemoveMembersRequest,
        request -> mapResponse(clusterConfigurationManagementApi.removeMembers(request)),
        this::encodeResponse);
  }

  private void registerJoinPartitionRequestsHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.JOIN_PARTITION.topic(),
        serializer::decodeJoinPartitionRequest,
        request -> mapResponse(clusterConfigurationManagementApi.joinPartition(request)),
        this::encodeResponse);
  }

  private void registerLeavePartitionRequestsHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.LEAVE_PARTITION.topic(),
        serializer::decodeLeavePartitionRequest,
        request -> mapResponse(clusterConfigurationManagementApi.leavePartition(request)),
        this::encodeResponse);
  }

  private void registerReassignPartitionRequestHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.REASSIGN_PARTITIONS.topic(),
        serializer::decodeReassignPartitionsRequest,
        request -> mapResponse(clusterConfigurationManagementApi.reassignPartitions(request)),
        this::encodeResponse);
  }

  private void registerScaleRequestHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.SCALE_MEMBERS.topic(),
        serializer::decodeScaleRequest,
        request -> mapResponse(clusterConfigurationManagementApi.scaleMembers(request)),
        this::encodeResponse);
  }

  private void registerForceScaleDownHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.FORCE_SCALE_DOWN.topic(),
        serializer::decodeScaleRequest,
        request -> mapResponse(clusterConfigurationManagementApi.forceScaleDown(request)),
        this::encodeResponse);
  }

  private void registerGetTopologyQueryHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.QUERY_TOPOLOGY.topic(),
        Function.identity(),
        request -> mapClusterTopologyResponse(clusterConfigurationManagementApi.getTopology()),
        this::encodeClusterTopologyResponse);
  }

  private void registerTopologyCancelHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.CANCEL_CHANGE.topic(),
        serializer::decodeCancelChangeRequest,
        request ->
            mapClusterTopologyResponse(
                clusterConfigurationManagementApi.cancelTopologyChange(request)),
        this::encodeClusterTopologyResponse);
  }

  private void registerDisableExporterHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.DISABLE_EXPORTER.topic(),
        serializer::decodeExporterDisableRequest,
        request -> mapResponse(clusterConfigurationManagementApi.disableExporter(request)),
        this::encodeResponse);
  }

  private void registerDeleteExporterHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.DELETE_EXPORTER.topic(),
        serializer::decodeExporterDeleteRequest,
        request -> mapResponse(clusterConfigurationManagementApi.deleteExporter(request)),
        this::encodeResponse);
  }

  private void registerEnableExporterHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.ENABLE_EXPORTER.topic(),
        serializer::decodeExporterEnableRequest,
        request -> mapResponse(clusterConfigurationManagementApi.enableExporter(request)),
        this::encodeResponse);
  }

  private void registerForceRemoveBrokersRequestHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.FORCE_REMOVE_BROKERS.topic(),
        serializer::decodeForceRemoveBrokersRequest,
        request -> mapResponse(clusterConfigurationManagementApi.forceRemoveBrokers(request)),
        this::encodeResponse);
  }

  private void registerPurgeRequestHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.PURGE.topic(),
        serializer::decodePurgeRequest,
        request -> mapResponse(clusterConfigurationManagementApi.purge(request)),
        this::encodeResponse);
  }

  private void registerClusterPatchRequestHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.PATCH_CLUSTER.topic(),
        serializer::decodeClusterPatchRequest,
        request -> mapResponse(clusterConfigurationManagementApi.patchCluster(request)),
        this::encodeResponse);
  }

  private void registerUpdateRoutingStateHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.UPDATE_ROUTING_STATE.topic(),
        serializer::decodeUpdateRoutingStateRequest,
        request -> mapResponse(clusterConfigurationManagementApi.updateRoutingState(request)),
        this::encodeResponse);
  }

  private void registerClusterScaleRequestHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.SCALE_CLUSTER.topic(),
        serializer::decodeClusterScaleRequest,
        request -> mapResponse(clusterConfigurationManagementApi.scaleCluster(request)),
        this::encodeResponse);
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> mapResponse(
      final ActorFuture<ClusterConfigurationChangeResponse> topologyManagementApi) {
    return topologyManagementApi
        .toCompletableFuture()
        .thenApply(Either::<ErrorResponse, ClusterConfigurationChangeResponse>right)
        .exceptionally(ClusterConfigurationRequestServer::mapError);
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfiguration>> mapClusterTopologyResponse(
      final ActorFuture<ClusterConfiguration> topologyManagementApi) {
    return topologyManagementApi
        .toCompletableFuture()
        .thenApply(Either::<ErrorResponse, ClusterConfiguration>right)
        .exceptionally(ClusterConfigurationRequestServer::mapError);
  }

  private static <T> Either<ErrorResponse, T> mapError(final Throwable throwable) {
    // throwable is always CompletionException
    return switch (throwable.getCause()) {
      case final ClusterConfigurationRequestFailedException.OperationNotAllowed
              operationNotAllowed ->
          Either.left(
              new ErrorResponse(ErrorCode.OPERATION_NOT_ALLOWED, operationNotAllowed.getMessage()));
      case final ClusterConfigurationRequestFailedException.InvalidRequest invalidRequest ->
          Either.left(new ErrorResponse(ErrorCode.INVALID_REQUEST, invalidRequest.getMessage()));
      case final ConcurrentModificationException concurrentModificationException ->
          Either.left(
              new ErrorResponse(
                  ErrorCode.CONCURRENT_MODIFICATION, concurrentModificationException.getMessage()));
      default -> Either.left(new ErrorResponse(ErrorCode.INTERNAL_ERROR, throwable.getMessage()));
    };
  }
}
