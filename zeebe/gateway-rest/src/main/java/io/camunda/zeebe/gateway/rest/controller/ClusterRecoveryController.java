/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Recovery operations that span the whole cluster, authenticated by the cluster-admin security
 * chain
 */
@CamundaRestController
@ClusterScoped
@RequestMapping("/cluster/v2")
@NullMarked
public final class ClusterRecoveryController {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterRecoveryController.class);

  private final ServiceRegistry serviceRegistry;

  public ClusterRecoveryController(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaPatchMapping(
      path = "/mode",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> changeClusterMode(
      @RequestParam final Mode mode,
      @RequestParam(required = false) final @Nullable String physicalTenantId,
      @RequestParam(name = "dryRun", defaultValue = "false") final boolean dryRun) {
    rejectBlankPhysicalTenantId(physicalTenantId);
    LOG.debug(
        "Requested cluster mode change to {} for {}",
        mode,
        physicalTenantId == null ? "all tenants" : physicalTenantId);
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterRecoveryServices()
                .changeMode(physicalTenantId, mode, dryRun)
                .thenApply(ClusterModeChangeMapper::unwrapOrThrow),
        ClusterModeChangeMapper::toClusterModeChangeResponse,
        HttpStatus.OK);
  }

  @CamundaPostMapping(path = "/restore")
  public CompletableFuture<ResponseEntity<Object>> restore(
      @RequestParam(required = false) final @Nullable String physicalTenantId,
      @RequestBody final io.camunda.gateway.protocol.model.ClusterRestoreRequest restoreRequest,
      @RequestParam(name = "dryRun", defaultValue = "false") final boolean dryRun) {
    rejectBlankPhysicalTenantId(physicalTenantId);
    LOG.info(
        "Requested restore for {}: {}",
        physicalTenantId == null ? "all tenants" : physicalTenantId,
        restoreRequest);
    final var overrides = restoreRequest.getOverrides();
    if (physicalTenantId != null && overrides != null && !overrides.isEmpty()) {
      throw new ServiceException(
          "Expected to restore physical tenant '%s', but the request also carries overrides for "
                  .formatted(physicalTenantId)
              + "other physical tenants. Overrides are only allowed for a cluster-wide restore.",
          Status.INVALID_ARGUMENT);
    }
    final var parameters = RestoreRequestMapper.toRestoreParameters(restoreRequest);
    final var overrideParameters =
        overrides == null
            ? Map.<String, RestoreParameters>of()
            : overrides.entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Entry::getKey,
                        entry -> RestoreRequestMapper.toRestoreParameters(entry.getValue())));
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterRecoveryServices()
                .restore(
                    Optional.ofNullable(physicalTenantId), parameters, overrideParameters, dryRun)
                .thenApply(ClusterModeChangeMapper::unwrapOrThrow),
        ClusterModeChangeMapper::toClusterRestoreResponse,
        HttpStatus.ACCEPTED);
  }

  private static void rejectBlankPhysicalTenantId(final @Nullable String physicalTenantId) {
    if (physicalTenantId != null && physicalTenantId.isBlank()) {
      throw new ServiceException(
          "Expected physicalTenantId to identify a physical tenant, but it was empty.",
          Status.INVALID_ARGUMENT);
    }
  }
}
