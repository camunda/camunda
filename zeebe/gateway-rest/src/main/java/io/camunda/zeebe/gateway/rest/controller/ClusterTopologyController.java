/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Reports the topology of the whole cluster, aggregated over all physical tenants (ADR 001 D5,
 * {@code docs/adr/management/001-physical-tenant-health-status-topology.md}).
 *
 * <p>This is the cluster-admin authenticated surface where physical tenant ids are exposed. {@code
 * GET /cluster/v2/status} is deliberately the tenant-id-free public counterpart.
 */
@CamundaRestController
@ClusterScoped
@RequestMapping("/cluster/v2")
public class ClusterTopologyController {

  private final ServiceRegistry serviceRegistry;

  public ClusterTopologyController(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaGetMapping(path = "/topology")
  public CompletableFuture<ResponseEntity<Object>> getClusterTopology() {
    return RequestExecutor.executeServiceMethod(
        serviceRegistry.clusterTopologyServices()::getTopology,
        ResponseMapper::toClusterTopologyResponse,
        HttpStatus.OK);
  }
}
