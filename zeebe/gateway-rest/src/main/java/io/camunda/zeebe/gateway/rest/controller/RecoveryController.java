/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.protocol.model.RestoreBrokerStatus;
import io.camunda.gateway.protocol.model.RestorePartitionStatus;
import io.camunda.gateway.protocol.model.RestoreStatusResponse;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.dynamic.config.api.RestoreStatus;
import io.camunda.zeebe.dynamic.config.api.RestoreStatus.BrokerRestoreStatus;
import io.camunda.zeebe.dynamic.config.api.RestoreStatus.PartitionRestoreStatus;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping(path = "/v2")
public final class RecoveryController {

  private static final Logger LOG = LoggerFactory.getLogger(RecoveryController.class);

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public RecoveryController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPatchMapping(
      path = "/mode",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> changeClusterMode(
      @PhysicalTenantId final String physicalTenantId,
      @RequestParam final Mode mode,
      @RequestParam(name = "dryRun", defaultValue = "false") final boolean dryRun) {
    LOG.debug("Requested cluster mode change to {} for physical tenant {}", mode, physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .recoveryServices(physicalTenantId)
                .changeMode(mode, dryRun, authentication)
                .thenApply(ClusterModeChangeMapper::unwrapOrThrow),
        ClusterModeChangeMapper::toClusterModeChangeResponse,
        HttpStatus.OK);
  }

  @CamundaPostMapping(path = "/restore")
  public CompletableFuture<ResponseEntity<Object>> restore(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false)
          final io.camunda.gateway.protocol.model.RestoreRequest restoreRequest,
      @RequestParam(name = "dryRun", defaultValue = "false") final boolean dryRun) {
    LOG.info("Requested restore for physical tenant {}: {}", physicalTenantId, restoreRequest);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var parameters = RestoreRequestMapper.toRestoreParameters(restoreRequest);
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .recoveryServices(physicalTenantId)
                .restore(parameters, dryRun, authentication)
                .thenApply(ClusterModeChangeMapper::unwrapOrThrow),
        ClusterModeChangeMapper::toClusterRestoreResponse,
        HttpStatus.ACCEPTED);
  }

  @CamundaGetMapping(path = "/restore")
  public CompletableFuture<ResponseEntity<Object>> getRestoreStatus(
      @PhysicalTenantId final String physicalTenantId) {
    LOG.debug("Requested restore status for physical tenant {}", physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .recoveryServices(physicalTenantId)
                .restoreStatus(authentication)
                .thenApply(ClusterModeChangeMapper::unwrapOrThrow)
                .thenApply(config -> partitionGroupConfigurationOrThrow(config, physicalTenantId))
                .thenApply(RecoveryController::restoreStatus),
        RecoveryController::mapRestoreStatus,
        HttpStatus.OK);
  }

  private static PartitionGroupConfiguration partitionGroupConfigurationOrThrow(
      final CurrentClusterConfiguration configuration, final String physicalTenantId) {
    if (!configuration.hasPartitionGroup(physicalTenantId)) {
      throw new ServiceException(
          "No configuration found for physical tenant '%s'".formatted(physicalTenantId),
          Status.NOT_FOUND);
    }
    return configuration.partitionGroup(physicalTenantId);
  }

  private static RestoreStatus restoreStatus(final PartitionGroupConfiguration configuration) {
    return RestoreStatus.of(configuration)
        .orElseThrow(
            () -> new ServiceException("No restore is currently in progress", Status.NOT_FOUND));
  }

  private static RestoreStatusResponse mapRestoreStatus(final RestoreStatus restore) {
    return RestoreStatusResponse.Builder.create()
        .status(mapStatusEnum(restore.status()))
        .changeId(Long.toString(restore.changeId()))
        .startedAt(toIso(restore.startedAt()))
        .brokers(restore.brokers().stream().map(RecoveryController::mapRestoreBroker).toList())
        .build();
  }

  private static RestoreBrokerStatus mapRestoreBroker(final BrokerRestoreStatus broker) {
    return RestoreBrokerStatus.Builder.create()
        .brokerId(broker.brokerId())
        .partitionsRestored(broker.partitionsRestored())
        .partitionsToRestore(broker.partitionsToRestore())
        .partitions(
            broker.partitions().stream().map(RecoveryController::mapRestorePartition).toList())
        .build();
  }

  private static RestorePartitionStatus mapRestorePartition(
      final PartitionRestoreStatus partition) {
    return RestorePartitionStatus.Builder.create()
        .partitionId(partition.partitionId())
        .state(mapPartitionState(partition.state()))
        .backupIds(partition.backupIds())
        .completedAt(toIso(partition.completedAt()))
        .build();
  }

  private static RestoreStatusResponse.StatusEnum mapStatusEnum(
      final ClusterChangePlan.Status status) {
    return switch (status) {
      case IN_PROGRESS -> RestoreStatusResponse.StatusEnum.IN_PROGRESS;
      case COMPLETED -> RestoreStatusResponse.StatusEnum.COMPLETED;
      case FAILED -> RestoreStatusResponse.StatusEnum.FAILED;
      case CANCELLED -> RestoreStatusResponse.StatusEnum.CANCELLED;
    };
  }

  private static RestorePartitionStatus.StateEnum mapPartitionState(
      final RestoreStatus.PartitionRestoreState state) {
    return switch (state) {
      case PENDING -> RestorePartitionStatus.StateEnum.PENDING;
      case RESTORING -> RestorePartitionStatus.StateEnum.RESTORING;
      case RESTORED -> RestorePartitionStatus.StateEnum.RESTORED;
    };
  }

  private static @Nullable String toIso(final @Nullable Instant instant) {
    return instant == null ? null : instant.toString();
  }
}
