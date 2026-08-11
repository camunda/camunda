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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.BackupResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * History (secondary-storage snapshot) backups for a single physical tenant.
 *
 * <p>The bean is registered unconditionally; {@link RequiresSecondaryStorage} makes the interceptor
 * answer 403 on a cluster whose secondary storage cannot serve these endpoints, and 503 while the
 * request's tenant's storage is degraded. The {@code /physical-tenants/{id}/v2/...} form of every
 * path comes from the physical-tenant request mapping, not from a second mapping here.
 */
@CamundaRestController
@RequiresSecondaryStorage({ELASTICSEARCH, OPENSEARCH})
@RequestMapping("/v2/backups/history")
public class HistoryBackupController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public HistoryBackupController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> takeBackup(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final TakeHistoryBackupRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .historyBackupServices(physicalTenantId)
                .takeBackup(request.getBackupId(), authentication),
        BackupResponseMapper::toTakeHistoryBackupResponse,
        HttpStatus.ACCEPTED);
  }

  @CamundaGetMapping
  public CompletableFuture<ResponseEntity<Object>> listBackups(
      @PhysicalTenantId final String physicalTenantId,
      @RequestParam(required = false) final String prefix,
      @RequestParam(required = false, defaultValue = "true") final boolean verbose) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .historyBackupServices(physicalTenantId)
                .listBackups(prefix, verbose, authentication),
        BackupResponseMapper::toHistoryBackupInfoList,
        HttpStatus.OK);
  }

  @CamundaGetMapping(path = "/{backupId}")
  public CompletableFuture<ResponseEntity<Object>> getBackup(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long backupId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .historyBackupServices(physicalTenantId)
                .getBackupState(backupId, authentication),
        BackupResponseMapper::toHistoryBackupInfo,
        HttpStatus.OK);
  }

  @CamundaDeleteMapping(path = "/{backupId}")
  public CompletableFuture<ResponseEntity<Object>> deleteBackup(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long backupId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .historyBackupServices(physicalTenantId)
                .deleteBackup(backupId, authentication));
  }
}
