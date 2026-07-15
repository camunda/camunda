/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.protocol.model.TakeRuntimeBackupRequest;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.BackupResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2/backups/runtime")
public class RuntimeBackupController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public RuntimeBackupController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> takeBackup(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final TakeRuntimeBackupRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .backupServices(physicalTenantId)
                .takeBackup(
                    Optional.ofNullable(request)
                        .map(TakeRuntimeBackupRequest::getBackupId)
                        .orElse(null),
                    authentication),
        BackupResponseMapper::toTakeBackupResponse,
        HttpStatus.ACCEPTED);
  }

  @CamundaGetMapping
  public CompletableFuture<ResponseEntity<Object>> listBackups(
      @PhysicalTenantId final String physicalTenantId,
      @RequestParam(required = false) final String prefix) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> serviceRegistry.backupServices(physicalTenantId).listBackups(prefix, authentication),
        BackupResponseMapper::toBackupInfoList,
        HttpStatus.OK);
  }

  @CamundaGetMapping(path = "/state")
  public CompletableFuture<ResponseEntity<Object>> getRuntimeBackupState(
      @PhysicalTenantId final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> serviceRegistry.backupServices(physicalTenantId).getRuntimeState(authentication),
        BackupResponseMapper::toRuntimeBackupState,
        HttpStatus.OK);
  }

  @CamundaPostMapping(
      path = "/state/sync",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> syncRuntimeBackupState(
      @PhysicalTenantId final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> serviceRegistry.backupServices(physicalTenantId).syncRuntimeState(authentication),
        BackupResponseMapper::toRuntimeBackupState,
        HttpStatus.OK);
  }

  @CamundaDeleteMapping(path = "/state")
  public CompletableFuture<ResponseEntity<Object>> deleteRuntimeBackupState(
      @PhysicalTenantId final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> serviceRegistry.backupServices(physicalTenantId).deleteRuntimeState(authentication));
  }

  @CamundaGetMapping(path = "/{backupId}")
  public CompletableFuture<ResponseEntity<Object>> getBackup(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long backupId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .backupServices(physicalTenantId)
                .getBackupStatus(backupId, authentication),
        BackupResponseMapper::toBackupInfo,
        HttpStatus.OK);
  }

  @CamundaDeleteMapping(path = "/{backupId}")
  public CompletableFuture<ResponseEntity<Object>> deleteBackup(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long backupId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .backupServices(physicalTenantId)
                .deleteBackup(backupId, authentication));
  }
}
