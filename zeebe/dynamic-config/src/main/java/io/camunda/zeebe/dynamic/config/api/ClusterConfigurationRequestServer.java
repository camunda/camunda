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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
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
    registerUpdatePartitionDistributionHandler();
    registerZoneMigrationHandler();
    registerForceRemoveBrokersRequestHandler();
    registerPurgeRequestHandler();
    registerRemovePhysicalTenantRequestHandler();
    registerModeChangeHandler();
    registerExportingStateChangeHandler();
    registerRestoreHandler();
    registerClusterRestoreHandler();
    registerForceRemoveZoneHandler();
    registerAddZoneHandler();
    registerUpdateZonePrioritiesHandler();
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

  byte[] encodeClusterTopologyResponse(
      final Either<ErrorResponse, CurrentClusterConfiguration> response) {
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
        request -> mapResponse(clusterConfigurationManagementApi.getTopology()),
        this::encodeClusterTopologyResponse);
  }

  private void registerTopologyCancelHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.CANCEL_CHANGE.topic(),
        serializer::decodeCancelChangeRequest,
        request -> mapResponse(clusterConfigurationManagementApi.cancelTopologyChange(request)),
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

  private void registerRemovePhysicalTenantRequestHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.REMOVE_PHYSICAL_TENANT.topic(),
        serializer::decodeRemovePhysicalTenantRequest,
        request -> mapResponse(clusterConfigurationManagementApi.removePhysicalTenant(request)),
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

  private void registerUpdatePartitionDistributionHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.UPDATE_PARTITION_DISTRIBUTION.topic(),
        serializer::decodeUpdatePartitionDistributorConfigRequest,
        request ->
            mapResponse(clusterConfigurationManagementApi.updatePartitionDistribution(request)),
        this::encodeResponse);
  }

  private void registerZoneMigrationHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.ZONE_MIGRATION.topic(),
        serializer::decodeClusterZoneMigrationRequest,
        request -> mapResponse(clusterConfigurationManagementApi.migrateZone(request)),
        this::encodeResponse);
  }

  private void registerClusterScaleRequestHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.SCALE_CLUSTER.topic(),
        serializer::decodeClusterScaleRequest,
        request -> mapResponse(clusterConfigurationManagementApi.scaleCluster(request)),
        this::encodeResponse);
  }

  private void registerModeChangeHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.MODE_CHANGE.topic(),
        serializer::decodeModeChangeRequest,
        request -> mapResponse(clusterConfigurationManagementApi.modeChange(request)),
        this::encodeResponse);
  }

  private void registerExportingStateChangeHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.EXPORTING_STATE_CHANGE.topic(),
        serializer::decodeExportingStateChangeRequest,
        request -> mapResponse(clusterConfigurationManagementApi.changeExportingState(request)),
        this::encodeResponse);
  }

  private void registerRestoreHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.RESTORE.topic(),
        serializer::decodeRestoreRequest,
        request -> mapResponse(clusterConfigurationManagementApi.restore(request)),
        this::encodeResponse);
  }

  private void registerClusterRestoreHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.CLUSTER_ADMIN_RESTORE.topic(),
        serializer::decodeClusterRestoreRequest,
        request -> mapResponse(clusterConfigurationManagementApi.clusterRestore(request)),
        this::encodeResponse);
  }

  private void registerForceRemoveZoneHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.FORCE_REMOVE_ZONE.topic(),
        serializer::decodeForceRemoveZoneRequest,
        request -> mapResponse(clusterConfigurationManagementApi.forceRemoveZone(request)),
        this::encodeResponse);
  }

  private void registerAddZoneHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.ADD_ZONE.topic(),
        serializer::decodeAddZoneRequest,
        request -> mapResponse(clusterConfigurationManagementApi.addZone(request)),
        this::encodeResponse);
  }

  private void registerUpdateZonePrioritiesHandler() {
    communicationService.replyTo(
        ClusterConfigurationRequestTopics.UPDATE_ZONE_PRIORITIES.topic(),
        serializer::decodeUpdateZonePrioritiesRequest,
        request -> mapResponse(clusterConfigurationManagementApi.updateZonePriorities(request)),
        this::encodeResponse);
  }

  private <T> CompletableFuture<Either<ErrorResponse, T>> mapResponse(
      final ActorFuture<T> topologyManagementApi) {
    return topologyManagementApi
        .toCompletableFuture()
        .thenApply(Either::<ErrorResponse, T>right)
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
      case final ClusterConfigurationRequestFailedException.InvalidState invalidState ->
          Either.left(new ErrorResponse(ErrorCode.INVALID_STATE, invalidState.getMessage()));
      case final ClusterConfigurationRequestFailedException.NotFound notFound ->
          Either.left(new ErrorResponse(ErrorCode.NOT_FOUND, notFound.getMessage()));
      case final ConcurrentModificationException concurrentModificationException ->
          Either.left(
              new ErrorResponse(
                  ErrorCode.CONCURRENT_MODIFICATION, concurrentModificationException.getMessage()));
      default -> Either.left(new ErrorResponse(ErrorCode.INTERNAL_ERROR, throwable.getMessage()));
    };
  }
}
