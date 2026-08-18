/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH;
import static io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH;

import io.camunda.gateway.protocol.model.TakeHistoryBackupRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.BackupResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * History (secondary-storage snapshot) backups across every physical tenant of the cluster,
 * authenticated by the cluster-admin security chain.
 *
 * <p>Every operation narrows to a single physical tenant when the request names one, which is the
 * only way to keep working while another tenant is broken: the fan-out is all-or-nothing.
 *
 * <p>{@link RequiresSecondaryStorage} answers 403 on a cluster whose storage cannot serve history
 * backups. It resolves the type through the request's physical tenant, which for an unstamped
 * {@code /cluster/v2} request is the default one — sound cluster-wide because {@code
 * SecondaryStorageTypeHomogeneityValidation} refuses a cluster that mixes document stores with
 * other storages, so one tenant's type answers for all of them.
 */
@CamundaRestController
@ClusterScoped
@RequiresSecondaryStorage({ELASTICSEARCH, OPENSEARCH})
@RequestMapping("/cluster/v2/backups/history")
@NullMarked
public final class ClusterHistoryBackupController {

  private final ServiceRegistry serviceRegistry;

  public ClusterHistoryBackupController(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> takeBackup(
      @RequestParam(required = false) final @Nullable String physicalTenantId,
      @RequestBody final TakeHistoryBackupRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterHistoryBackupServices()
                .takeBackup(targetPhysicalTenant(physicalTenantId), request.getBackupId()),
        BackupResponseMapper::toClusterTakeHistoryBackupResponse,
        HttpStatus.ACCEPTED);
  }

  @CamundaGetMapping
  public CompletableFuture<ResponseEntity<Object>> listBackups(
      @RequestParam(required = false) final @Nullable String physicalTenantId,
      @RequestParam(required = false) final @Nullable String prefix,
      @RequestParam(required = false, defaultValue = "true") final boolean verbose) {
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterHistoryBackupServices()
                .listBackups(targetPhysicalTenant(physicalTenantId), prefix, verbose),
        BackupResponseMapper::toClusterHistoryBackupInfoList,
        HttpStatus.OK);
  }

  @CamundaGetMapping(path = "/{backupId}")
  public CompletableFuture<ResponseEntity<Object>> getBackup(
      @PathVariable final long backupId,
      @RequestParam(required = false) final @Nullable String physicalTenantId) {
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterHistoryBackupServices()
                .getBackup(targetPhysicalTenant(physicalTenantId), backupId),
        BackupResponseMapper::toClusterHistoryBackupInfo,
        HttpStatus.OK);
  }

  @CamundaDeleteMapping(path = "/{backupId}")
  public CompletableFuture<ResponseEntity<Object>> deleteBackup(
      @PathVariable final long backupId,
      @RequestParam(required = false) final @Nullable String physicalTenantId) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .clusterHistoryBackupServices()
                .deleteBackup(targetPhysicalTenant(physicalTenantId), backupId));
  }

  /**
   * Resolves which physical tenant an operation targets: the named tenant, or {@code null} when the
   * request names none and the operation therefore spans every physical tenant of the cluster. A
   * blank id names no tenant, so it is cluster-wide as well rather than a request the cluster-admin
   * API rejects — the same handling the cluster recovery endpoints apply.
   */
  private static @Nullable String targetPhysicalTenant(final @Nullable String physicalTenantId) {
    return physicalTenantId == null || physicalTenantId.isBlank() ? null : physicalTenantId;
  }
}
