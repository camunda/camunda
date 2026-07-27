/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.TopologyServices.ClusterStatus;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * {@code GET /v2/status} is scoped to the default physical tenant only (ADR 001 D3): it is exposed
 * unprefixed and at {@code /physical-tenants/default/v2/status}, both reaching this controller.
 * {@code /physical-tenants/{id}/v2/status} for any other id gets a uniform 404 from {@link
 * PhysicalTenantStatusScopeFilter}, before this controller — or Spring Security — is ever reached,
 * so unauthenticated callers cannot enumerate physical tenant ids.
 */
@CamundaRestController
@RequestMapping("/v2")
public class StatusController {

  private final ServiceRegistry serviceRegistry;

  public StatusController(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaGetMapping(path = "/status")
  public CompletableFuture<ResponseEntity<Object>> getStatus(
      @PhysicalTenantId final String physicalTenantId) {
    return RequestExecutor.executeServiceMethod(
        serviceRegistry.topologyServices(physicalTenantId)::getStatus,
        StatusController::getStatusResponse);
  }

  private static ResponseEntity<Object> getStatusResponse(final ClusterStatus clusterStatus) {
    return ClusterStatus.HEALTHY.equals(clusterStatus)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }
}
