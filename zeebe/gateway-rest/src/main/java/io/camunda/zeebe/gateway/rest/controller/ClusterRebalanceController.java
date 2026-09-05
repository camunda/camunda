/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.protocol.model.ClusterRebalanceRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Coordinated cluster-wide leadership rebalance, authenticated by the cluster-admin security chain.
 *
 * <p>Requests are forwarded to the current rebalance coordinator.
 */
@CamundaRestController
@ClusterScoped
@RequestMapping("/cluster/v2")
@NullMarked
public final class ClusterRebalanceController {

  private final ServiceRegistry serviceRegistry;

  public ClusterRebalanceController(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaPostMapping(path = "/rebalance")
  public CompletableFuture<ResponseEntity<Object>> triggerRebalance(
      @RequestParam(defaultValue = "false") final boolean dryRun,
      @RequestBody(required = false) final @Nullable ClusterRebalanceRequest body) {
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .clusterRebalanceServices()
                .triggerRebalance(ClusterRebalanceMapper.toServiceRequest(dryRun, body)),
        ClusterRebalanceMapper::toClusterBalanceResponse,
        HttpStatus.ACCEPTED);
  }

  @CamundaGetMapping(path = "/rebalance")
  public CompletableFuture<ResponseEntity<Object>> getRebalanceStatus() {
    return RequestExecutor.executeServiceMethod(
        serviceRegistry.clusterRebalanceServices()::getRebalanceStatus,
        ClusterRebalanceMapper::toClusterBalanceResponse,
        HttpStatus.OK);
  }

  @CamundaDeleteMapping(path = "/rebalance")
  public CompletableFuture<ResponseEntity<Object>> cancelRebalance() {
    return RequestExecutor.executeServiceMethod(
        serviceRegistry.clusterRebalanceServices()::cancelRebalance,
        ClusterRebalanceMapper::toRebalanceCancellationResponse,
        HttpStatus.OK);
  }
}
