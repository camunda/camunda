/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2/exporting")
public class ExportingController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ExportingController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(
      path = "/pause",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> pauseExporting(
      @PhysicalTenantId final String physicalTenantId,
      @RequestParam(defaultValue = "false") final boolean soft) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .exportingServices(physicalTenantId)
                .pauseExporting(soft, authentication));
  }

  @CamundaPostMapping(
      path = "/resume",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> resumeExporting(
      @PhysicalTenantId final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> serviceRegistry.exportingServices(physicalTenantId).resumeExporting(authentication));
  }
}
