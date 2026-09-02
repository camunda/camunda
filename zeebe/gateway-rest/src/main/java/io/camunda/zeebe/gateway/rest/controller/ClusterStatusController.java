/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.ClusterStatusResponse;
import io.camunda.gateway.protocol.model.ClusterStatusResponse.StatusEnum;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Reports one aggregated status for the whole cluster, over all physical tenants (ADR 001 D4,
 * {@code docs/adr/management/001-physical-tenant-health-status-topology.md}).
 *
 * <p>This endpoint is unauthenticated via {@code SecurityPathAdapter.unprotectedPaths()} so that it
 * can be polled by monitoring. That is why the response carries the aggregated status only: a
 * physical tenant id, or even a tenant count, would let an unauthenticated caller enumerate the
 * cluster's tenants. Per-tenant detail is served by the cluster-admin authenticated {@code GET
 * /cluster/v2/topology}.
 */
@CamundaRestController
@ClusterScoped
@RequestMapping("/cluster/v2")
public class ClusterStatusController {

  private final ServiceRegistry serviceRegistry;

  public ClusterStatusController(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaGetMapping(path = "/status")
  public CompletableFuture<ResponseEntity<Object>> getClusterStatus() {
    return RequestExecutor.executeServiceMethod(
        serviceRegistry.clusterStatusServices()::getStatus,
        ResponseMapper::toClusterStatusResponse,
        ClusterStatusController::httpStatus);
  }

  /**
   * Reports one overall upgrade-readiness status for the whole cluster (see the {@code
   * upgradeReadiness} actuator endpoint for full per-physical-tenant, per-condition detail). Public
   * and unauthenticated, like {@link #getClusterStatus()} — see camunda/camunda#61619.
   *
   * <p>Always answers {@code 200}, even for {@code MIGRATION_IN_PROGRESS}/{@code UNKNOWN}: an
   * in-progress upgrade is an expected condition the cluster keeps serving traffic through, unlike
   * {@link #getClusterStatus()}'s {@code DOWN}, which is a genuine outage.
   */
  @CamundaGetMapping(path = "/status/upgrade")
  public CompletableFuture<ResponseEntity<Object>> getClusterUpgradeStatus() {
    return RequestExecutor.executeServiceMethod(
        serviceRegistry.clusterUpgradeStatusServices()::getStatus,
        ResponseMapper::toClusterUpgradeStatusResponse,
        HttpStatus.OK);
  }

  /**
   * Only {@code DOWN} means the cluster cannot process work; a degraded cluster still serves
   * traffic, so it must not be signalled as unavailable.
   */
  private static HttpStatus httpStatus(final ClusterStatusResponse response) {
    return StatusEnum.DOWN.equals(response.getStatus())
        ? HttpStatus.SERVICE_UNAVAILABLE
        : HttpStatus.OK;
  }
}
