/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
