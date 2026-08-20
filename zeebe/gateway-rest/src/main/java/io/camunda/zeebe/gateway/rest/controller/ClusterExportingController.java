/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.mapper.ExportingResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Pauses, resumes, and reports exporting status across every physical tenant of the cluster in one
 * call (ADR 003 D2), served by the cluster-admin security chain.
 */
@CamundaRestController
@ClusterScoped
@RequestMapping("/cluster/v2")
@NullMarked
public final class ClusterExportingController {

  private final ServiceRegistry serviceRegistry;

  public ClusterExportingController(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaGetMapping(path = "/exporting")
  public CompletableFuture<ResponseEntity<Object>> getClusterExportingStatus() {
    return RequestExecutor.executeServiceMethod(
        serviceRegistry.clusterExportingServices()::getExportingStatus,
        ExportingResponseMapper::toExportingStatusResponse,
        HttpStatus.OK);
  }

  @CamundaPostMapping(
      path = "/exporting/pause",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> pauseClusterExporting(
      @RequestParam(defaultValue = "false") final boolean soft) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> serviceRegistry.clusterExportingServices().pauseExporting(soft));
  }

  @CamundaPostMapping(
      path = "/exporting/resume",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> resumeClusterExporting() {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> serviceRegistry.clusterExportingServices().resumeExporting());
  }
}
