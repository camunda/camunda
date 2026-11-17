/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.protocol.model.TakeRuntimeBackupRequest;
import io.camunda.service.ClusterRuntimeBackupServices.ClusterRuntimeBackupTaken;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.mapper.BackupResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Runtime (primary-storage) backups across every physical tenant of the cluster, authenticated by
 * the cluster-admin security chain (ADR 003 D2).
 *
 * <p>Every operation narrows to a single physical tenant when the request names one, which is the
 * only way to keep working while another tenant is broken: the reads and deletes are
 * all-or-nothing.
 *
 * <p>Unlike {@link ClusterHistoryBackupController} this needs no {@code @RequiresSecondaryStorage}
 * gate: runtime backups are taken from the brokers' primary storage, which every cluster has
 * whatever its secondary storage is.
 */
@CamundaRestController
@ClusterScoped
@RequestMapping("/cluster/v2/backups/runtime")
@NullMarked
public final class ClusterRuntimeBackupController {

  private final ServiceRegistry serviceRegistry;

  public ClusterRuntimeBackupController(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  /**
   * Answers 202 when every targeted physical tenant was triggered, and an error status otherwise —
   * but with the same body either way, because a partial trigger leaves backups running that the
   * caller has to know about to monitor or delete (ADR 003 D4). A request rejected before any
   * tenant was triggered fails in the service instead and answers with a problem detail, so the two
   * cases stay distinguishable.
   */
  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> takeBackup(
      @RequestParam(required = false) final @Nullable String physicalTenantId,
      @RequestBody(required = false) final @Nullable TakeRuntimeBackupRequest request) {
    final var backupId =
        Optional.ofNullable(request).map(TakeRuntimeBackupRequest::getBackupId).orElse(null);
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterRuntimeBackupServices()
                .takeBackup(targetPhysicalTenant(physicalTenantId), backupId),
        ClusterRuntimeBackupController::toTakeBackupResponse);
  }

  @CamundaGetMapping
  public CompletableFuture<ResponseEntity<Object>> listBackups(
      @RequestParam(required = false) final @Nullable String physicalTenantId,
      @RequestParam(required = false) final @Nullable String prefix) {
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterRuntimeBackupServices()
                .listBackups(targetPhysicalTenant(physicalTenantId), prefix),
        BackupResponseMapper::toClusterRuntimeBackupInfoList,
        HttpStatus.OK);
  }

  @CamundaGetMapping(path = "/state")
  public CompletableFuture<ResponseEntity<Object>> getRuntimeBackupState(
      @RequestParam(required = false) final @Nullable String physicalTenantId) {
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterRuntimeBackupServices()
                .getRuntimeState(targetPhysicalTenant(physicalTenantId)),
        BackupResponseMapper::toClusterRuntimeBackupState,
        HttpStatus.OK);
  }

  @CamundaPostMapping(
      path = "/state/sync",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> syncRuntimeBackupState(
      @RequestParam(required = false) final @Nullable String physicalTenantId) {
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterRuntimeBackupServices()
                .syncRuntimeState(targetPhysicalTenant(physicalTenantId)),
        BackupResponseMapper::toClusterRuntimeBackupState,
        HttpStatus.OK);
  }

  @CamundaDeleteMapping(path = "/state")
  public CompletableFuture<ResponseEntity<Object>> deleteRuntimeBackupState(
      @RequestParam(required = false) final @Nullable String physicalTenantId) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .clusterRuntimeBackupServices()
                .deleteRuntimeState(targetPhysicalTenant(physicalTenantId)));
  }

  @CamundaGetMapping(path = "/{backupId}")
  public CompletableFuture<ResponseEntity<Object>> getBackup(
      @PathVariable final long backupId,
      @RequestParam(required = false) final @Nullable String physicalTenantId) {
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterRuntimeBackupServices()
                .getBackup(targetPhysicalTenant(physicalTenantId), backupId),
        BackupResponseMapper::toClusterRuntimeBackupInfo,
        HttpStatus.OK);
  }

  @CamundaDeleteMapping(path = "/{backupId}")
  public CompletableFuture<ResponseEntity<Object>> deleteBackup(
      @PathVariable final long backupId,
      @RequestParam(required = false) final @Nullable String physicalTenantId) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .clusterRuntimeBackupServices()
                .deleteBackup(targetPhysicalTenant(physicalTenantId), backupId));
  }

  private static ResponseEntity<Object> toTakeBackupResponse(
      final ClusterRuntimeBackupTaken taken) {
    final var status =
        taken.failureStatus() == null
            ? HttpStatus.ACCEPTED
            : GatewayErrorMapper.mapStatus(taken.failureStatus());
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(BackupResponseMapper.toClusterTakeRuntimeBackupResponse(taken));
  }

  /**
   * Resolves which physical tenant an operation targets: the named tenant, or {@code null} when the
   * request names none and the operation therefore spans every physical tenant of the cluster. A
   * blank id names no tenant, so it is cluster-wide as well rather than a request the cluster-admin
   * API rejects — the same handling the other cluster-admin endpoints apply.
   */
  private static @Nullable String targetPhysicalTenant(final @Nullable String physicalTenantId) {
    return physicalTenantId == null || physicalTenantId.isBlank() ? null : physicalTenantId;
  }
}
